package co.zzyun.memcached

import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer

interface MemcachedClient {
  fun get(key: String, completionHandler: Handler<AsyncResult<Buffer>>)
  fun set(key: String, value:Buffer,completionHandler: Handler<AsyncResult<Void>>)
  fun delete(key:String)
  fun close()
  companion object {
    fun connect(vertx:Vertx,port:Int,host:String,completionHandler: Handler<AsyncResult<MemcachedClient>>){
      MemcachedClientImpl(vertx,port,host).connect(completionHandler)
    }
  }
}
