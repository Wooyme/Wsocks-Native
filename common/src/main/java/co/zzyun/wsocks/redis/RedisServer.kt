package co.zzyun.wsocks.redis

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.redis.RedisClient
import io.vertx.redis.RedisOptions
import java.util.concurrent.ConcurrentHashMap

class RedisServer(private val vertx: Vertx) {
  class ConnectInfo(private val vertx: Vertx,private val nextIp:String,private val id:String,val info: JsonObject){
    companion object {
      val clientMap = ConcurrentHashMap<String,RedisClient>()
    }
    private lateinit var connection: RedisConnection
    fun reject(){
      (clientMap[nextIp]?: RedisClient.create(vertx, RedisOptions().apply {
        val remote = nextIp.split(":")
        this.host = remote[0]
        if(remote.size>1) this.port = remote[1].toInt()
      }).also {
        clientMap[nextIp]=it
      }).setBinary(id,Buffer.buffer("reject")){}
    }

    fun connect():RedisConnection{
      connection = RedisConnection(id,vertx,(clientMap[nextIp]?: RedisClient.create(vertx, RedisOptions().apply {
        val remote = nextIp.split(":")
        this.host = remote[0]
        if(remote.size>1) this.port = remote[1].toInt()
      }).also { clientMap[nextIp]=it }),info)
      connection.stopHandler(Handler {
        clientMap.remove(nextIp)
      })
      connection.start()
      return connection
    }
    fun stop(){
      this.connection.stop()
    }
    fun write(data: Buffer){
      connection.write(data)
    }
  }
  private lateinit var centerServer: String
  private lateinit var client: RedisClient
  private var timerId: Long = 0
  private lateinit var onConnect:Handler<ConnectInfo>
  fun start(name: String, centerServer: String) {
    this.centerServer = centerServer
    client = RedisClient.create(vertx,RedisOptions().apply {
      val remote = centerServer.split(":")
      this.host = remote[0]
      if(remote.size>1) this.port = remote[1].toInt()
    })
    timerId = vertx.setPeriodic(200) {
      client.getBinary(name){
        if(it.succeeded()) {
          if (it.result() != null) {
            println(it.result())
            client.del(name) {}
            val json = try {
              it.result().toJsonObject()
            } catch (e: Throwable) {
              return@getBinary
            }
            val nextIp = json.getString("next")
            val id = json.getString("id")
            onConnect.handle(ConnectInfo(vertx, nextIp, id, json.getJsonObject("info")))
          }
        }else{
          this.stop()
          this.start(name,centerServer)
        }
      }
    }
  }

  fun onConnect(handler: Handler<ConnectInfo>):RedisServer{
    this.onConnect = handler
    return this
  }

  fun stop() {
    vertx.cancelTimer(timerId)
    this.client.close{}
  }
}
