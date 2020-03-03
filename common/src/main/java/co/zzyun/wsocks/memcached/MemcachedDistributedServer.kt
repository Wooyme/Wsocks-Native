package co.zzyun.wsocks.memcached

import com.spotify.folsom.MemcacheClient
import com.spotify.folsom.MemcacheClientBuilder
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer

class MemcachedDistributedServer(private val vertx: Vertx, center: String, private val name: String) {
  private val center: MemcacheClient<ByteArray> = MemcacheClientBuilder.newByteArrayClient().withAddress(center, 11211).connectBinary()
  private lateinit var connectionHandler: Handler<MemcachedDistributedConnection>
  private var timerId = 0L
  fun start() {
    timerId = vertx.setPeriodic(200) {
      center.get(name).handle { r, e ->
        if(r==null) return@handle
        center.delete(name)
        val json = Buffer.buffer(r).toJsonObject()
        val nodeList = json.getJsonArray("nodes").list as List<String>
        val id = json.getString("id")
        val info = json.getJsonObject("info")
        connectionHandler.handle(MemcachedDistributedConnection(vertx, center, id, nodeList, info))
      }
    }
  }

  fun handler(connectionHandler: Handler<MemcachedDistributedConnection>): MemcachedDistributedServer {
    this.connectionHandler = connectionHandler
    return this
  }

  fun stop(){
    vertx.cancelTimer(timerId)
    center.shutdown()
  }

}
