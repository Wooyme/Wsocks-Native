package co.zzyun.wsocks.redis

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.redis.RedisClient
import io.vertx.redis.RedisOptions
import org.apache.commons.lang3.RandomStringUtils
import java.lang.IllegalStateException

class RedisClient(private val vertx: Vertx, private val centerServer: String,private val port:Int = 6379){
  private val centerClient = RedisClient.create(vertx, RedisOptions().setHost(centerServer).setPort(port))
  private lateinit var client: RedisClient
  private var timerId:Long = 0
  private lateinit var handler: Handler<Buffer>
  private lateinit var shutdownHandler: (String)->Any
  private lateinit var successHandler: ()->Any
  private var succeeded = false
  lateinit var connection: RedisConnection
  fun start(name:String,nextIp:String,headers: JsonObject){
    client = RedisClient.create(vertx,RedisOptions().apply {
      val remote = nextIp.split(":")
      this.host = remote[0]
      if(remote.size>1) this.port = remote[1].toInt()
    })
    val id = RandomStringUtils.randomAlphanumeric(16)
    centerClient.setBinary(name,JsonObject().put("info",headers).put("id",id).put("next",nextIp).toBuffer()){}
    timerId = vertx.setPeriodic(200){
      client.getBinary(id) {
        if(it.succeeded() && it.result()!=null){
          client.del(id){
            println(it.succeeded())
          }
          println(it.result().toString())
          when(it.result().toString()){
            "success"->{
              if(!succeeded) {
                successHandler.invoke()
                connection = RedisConnection(id, vertx, client, JsonObject(), "s" to "c")
                connection.handler(handler)
                connection.stopHandler(Handler {
                  this.stop("Connection broke")
                })
                succeeded = true
              }
            }
            "shutdown"->{
              println("Shutdown from remote")
              this.stop("Shutdown from remote")
            }
            "reject"->{
              println("reject by remote")
              this.stop("Rejected by remote")
            }
          }
        }
      }
    }
    vertx.setTimer(16*1000){
      if(!succeeded){
        println("Timeout")
        this.stop("Timeout")
      }
    }
  }
  fun write(data:Buffer){
    if(this::connection.isInitialized){
      connection.write(data)
    }else{
      throw IllegalStateException("No connection")
    }
  }

  fun handler(handler: Handler<Buffer>):co.zzyun.wsocks.redis.RedisClient{
    this.handler = handler
    return this
  }

  fun successHandler(handler:()->Any):co.zzyun.wsocks.redis.RedisClient{
    this.successHandler = handler
    return this
  }

  fun shutdownHandler(handler:(String)->Any):co.zzyun.wsocks.redis.RedisClient{
    this.shutdownHandler = handler
    return this
  }

  fun stop(reason:String){
    vertx.cancelTimer(timerId)
    if(this::connection.isInitialized)
      this.connection.stop()
    this.client.close{}
    this.shutdownHandler.invoke(reason)
  }
}
