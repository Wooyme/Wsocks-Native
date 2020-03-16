package co.zzyun.wsocks.memcached

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import net.spy.memcached.BinaryConnectionFactory
import net.spy.memcached.MemcachedClient
import org.apache.commons.lang3.RandomStringUtils
import java.lang.IllegalStateException
import java.net.InetSocketAddress

class MemcachedClient(private val vertx: Vertx, private val centerServer: String) {
  private val centerClient: MemcachedClient = MemcachedClient(BinaryConnectionFactory(), listOf(InetSocketAddress(centerServer, 11211)))
  private lateinit var client: MemcachedClient
  private var timerId: Long = 0
  private lateinit var handler: Handler<Buffer>
  private lateinit var shutdownHandler: (String) -> Any
  private lateinit var successHandler: () -> Any
  private var succeeded = false
  private var stopped = false
  lateinit var connection: MemcachedConnection
  fun start(name: String, nextIp: String, headers: JsonObject) {
    client = MemcachedClient(BinaryConnectionFactory(), listOf(InetSocketAddress(nextIp, 11211)))
    val id = RandomStringUtils.randomAlphanumeric(16)
    client.delete(id)
    Thread.sleep(500)
    centerClient.set(name, 1000, DataNode(JsonObject().put("info", headers).put("id", id).put("next", nextIp).toBuffer()), MyTranscoder.instance)
    timerId = vertx.setPeriodic(200) {
      client.asyncGet(id, MyTranscoder.instance).addListener {
        val dataNode = it.get() as DataNode? ?: return@addListener
        if (!stopped) {
          if (dataNode.isSuccess()) {
            client.delete(id)
            if (!succeeded) {
              successHandler.invoke()
              connection = MemcachedConnection(id, vertx, client, JsonObject(), "s" to "c")
              connection.handler(handler)
              connection.stopHandler(Handler {
                this.stop("Connection broke")
              })
              connection.start()
              succeeded = true
            }
          } else if (dataNode.isShutdown()) {
            println("Shutdown from remote")
            client.delete(id)
            this.stop("Shutdown from remote")
          }
        }
      }
    }
    vertx.setTimer(8 * 1000) {
      if (!succeeded) {
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

  fun handler(handler: Handler<Buffer>): co.zzyun.wsocks.memcached.MemcachedClient {
    this.handler = handler
    return this
  }

  fun successHandler(handler: () -> Any): co.zzyun.wsocks.memcached.MemcachedClient {
    this.successHandler = handler
    return this
  }

  fun shutdownHandler(handler: (String) -> Any): co.zzyun.wsocks.memcached.MemcachedClient {
    this.shutdownHandler = handler
    return this
  }

  fun stop(reason: String) {
    stopped = true
    vertx.cancelTimer(timerId)
    if (this::connection.isInitialized)
      this.connection.stop()
    this.client.shutdown()
    this.shutdownHandler.invoke(reason)
  }
}
