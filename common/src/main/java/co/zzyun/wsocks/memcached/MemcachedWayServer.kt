package co.zzyun.wsocks.memcached

import co.zzyun.memcached.MemcachedClient
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject

class MemcachedWayServer(private val vertx: Vertx) {
  class ConnectInfo(private val vertx: Vertx, private val nextIp: String, private val id: String, val info: JsonObject) {
    private lateinit var connection: MemcachedWayConnection
    fun reject() {
      MemcachedClient.connect(vertx,11211,nextIp, Handler {
        it.result()?.let{
          it.set(id,DataNode.reject.buffer, Handler {_->
            it.close()
          })
        }
      })
    }

    fun connect(): Future<MemcachedWayConnection> {
      val fut = Future.future<MemcachedWayConnection>()
      MemcachedClient.connect(vertx,11211,nextIp, Handler {
        it.result()?.let {
          connection = MemcachedWayConnection(id,vertx,it,info)
          connection.start()
          fut.complete(connection)
        }?:fut.fail(it.cause())
      })
      return fut
    }

    fun stop() {
      this.connection.stop()
    }

    fun write(data: Buffer) {
      connection.write(data)
    }
  }

  private lateinit var centerServer: String
  private lateinit var client: MemcachedClient
  private var timerId: Long = 0
  private lateinit var onConnect: Handler<ConnectInfo>
  fun start(name: String, centerServer: String) {
    this.centerServer = centerServer
    MemcachedClient.connect(vertx, 11211, centerServer, Handler {
      if (it.failed()) {
        it.cause().printStackTrace()
      } else {
        client = it.result()
        client.delete(name)
        Thread.sleep(500)
        timerId = vertx.setPeriodic(200) {
          client.get(name, Handler {
            if (it.failed()) {
              this.stop()
              this.start(name, centerServer)
              return@Handler
            }
            val data = it.result()?.let { DataNode(it) } ?: return@Handler
            client.delete(name)
            val json = data.buffer.toJsonObject()
            val nextIp = json.getString("next")
            val id = json.getString("id")
            onConnect.handle(ConnectInfo(vertx, nextIp, id, json.getJsonObject("info")))
          })
        }
      }
    })
  }

  fun onConnect(handler: Handler<ConnectInfo>): MemcachedWayServer {
    this.onConnect = handler
    return this
  }

  fun stop() {
    vertx.cancelTimer(timerId)
    this.client.close()
  }
}
