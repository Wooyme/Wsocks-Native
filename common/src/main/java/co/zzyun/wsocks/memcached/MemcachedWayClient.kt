package co.zzyun.wsocks.memcached

import co.zzyun.memcached.MemcachedClient
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import org.apache.commons.lang3.RandomStringUtils

class MemcachedWayClient(private val vertx: Vertx, private val centerServer: String) {
  private lateinit var centerClient: MemcachedClient
  private lateinit var client: MemcachedClient
  private var timerId: Long = 0
  private lateinit var handler: Handler<Buffer>
  private lateinit var shutdownHandler: (String) -> Any
  private lateinit var successHandler: () -> Any
  private var finished = false
  private var stopped = false
  lateinit var connection: MemcachedWayConnection
  fun start(name: String, nextIp: String, headers: JsonObject) {
    val fut1 = Future.future<MemcachedClient>()
    MemcachedClient.connect(vertx, 11211, centerServer, Handler {
      if (it.succeeded()) {
        fut1.complete(it.result())
      } else {
        fut1.fail("Cannot connect to center server")
      }
    })
    val fut2 = Future.future<MemcachedClient>()
    MemcachedClient.connect(vertx, 11211, nextIp, Handler {
      if (it.succeeded()) {
        fut2.complete(it.result())
      } else {
        fut2.fail("Cannot connect to center server")
      }
    })
    CompositeFuture.all(fut1, fut2).setHandler {
      if (it.failed()) return@setHandler this.stop(it.cause().message!!)
      centerClient = it.result().list<MemcachedClient>()[0]
      client = it.result().list<MemcachedClient>()[1]
      val id = RandomStringUtils.randomAlphanumeric(16)
      client.delete(id)
      Thread.sleep(500)
      centerClient.set(name, JsonObject().put("info", headers).put("id", id).put("next", nextIp).toBuffer(), Handler { })
      timerId = vertx.setPeriodic(200) {
        client.get(id, Handler {
          if (it.failed())
            return@Handler this.stop(it.cause().localizedMessage)
          val dataNode = it.result()?.let { DataNode(it) } ?: return@Handler
          if (!stopped) {
            if (dataNode.isSuccess()) {
              client.delete(id)
              if (!finished) {
                successHandler.invoke()
                connection = MemcachedWayConnection(id, vertx, client, JsonObject(), "s" to "c")
                connection.handler(handler)
                connection.stopHandler(Handler {
                  this.stop("Connection broke")
                })
                connection.start()
                finished = true
              }
            } else if (dataNode.isShutdown()) {
              println("Shutdown from remote")
              client.delete(id)
              this.stop("Shutdown from remote")
              finished = true
            } else if (dataNode.isReject()) {
              println("Reject by remote")
              client.delete(id)
              this.stop("Reject by remote")
              finished = true
            }
          }
        })
      }
    }

    vertx.setTimer(12 * 1000) {
      if (!finished) {
        println("Timeout")
        this.stop("Timeout")
      }
    }
  }

  fun write(data: Buffer) {
    if (!stopped) {
      if (this::connection.isInitialized) {
        connection.write(data)
      } else {
        throw IllegalStateException("No connection")
      }
    }
  }

  fun handler(handler: Handler<Buffer>): MemcachedWayClient {
    this.handler = handler
    return this
  }

  fun successHandler(handler: () -> Any): MemcachedWayClient {
    this.successHandler = handler
    return this
  }

  fun shutdownHandler(handler: (String) -> Any): MemcachedWayClient {
    this.shutdownHandler = handler
    return this
  }

  fun stop(reason: String) {
    stopped = true
    vertx.cancelTimer(timerId)
    if (this::connection.isInitialized)
      this.connection.stop()
    if(this::client.isInitialized)
      this.client.close()
    if(this::centerClient.isInitialized)
      this.centerClient.close()
    this.shutdownHandler.invoke(reason)
  }
}
