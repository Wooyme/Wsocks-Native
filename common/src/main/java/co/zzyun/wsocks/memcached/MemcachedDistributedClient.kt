package co.zzyun.wsocks.memcached

import com.spotify.folsom.MemcacheClient
import com.spotify.folsom.MemcacheClientBuilder
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

class MemcachedDistributedClient(private val vertx: Vertx, center: String) {
  private val center: MemcacheClient<ByteArray> = MemcacheClientBuilder.newByteArrayClient().withAddress(center, 11211).connectBinary()
  private lateinit var connection: MemcachedDistributedConnection
  private lateinit var handler:Handler<Buffer>
  private var timerId = 0L
  fun start(id:String,name:String,nodes:List<String>,headers:JsonObject){
    timerId = vertx.setPeriodic(200) {
      center.get(id).handle { r, e ->
        if(r==null) return@handle
        center.delete(name)
        val code = Buffer.buffer(r).toString()
        if(code=="success"){
          connection = MemcachedDistributedConnection(vertx,center,id,nodes,JsonObject(),"s" to "c").handler(handler)
          connection.connect()
        }
      }
    }
    center.set(name,JsonObject().put("info",headers).put("nodes", JsonArray(nodes)).put("id",id).toBuffer().bytes,600)
  }

  fun handler(handler: Handler<Buffer>):MemcachedDistributedClient{
    this.handler = handler
    return this
  }

  fun stop(){
    vertx.cancelTimer(timerId)
    center.shutdown()
    connection.stop()
  }
}
