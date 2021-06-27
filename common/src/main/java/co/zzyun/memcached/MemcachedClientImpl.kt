package co.zzyun.memcached

import io.netty.buffer.Unpooled
import io.netty.handler.codec.memcache.binary.*
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.impl.NetSocketInternal
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue


class MemcachedClientImpl(private val vertx: Vertx,private val port:Int,private val host:String):MemcachedClient {
  private val inflight = ConcurrentLinkedQueue<Handler<AsyncResult<FullBinaryMemcacheResponse>>>()
  private val netClient by lazy { vertx.createNetClient() }
  private lateinit var so:NetSocketInternal
  fun connect(completionHandler: Handler<AsyncResult<MemcachedClient>>){
    val future = Future.future<MemcachedClient>().setHandler(completionHandler)
    netClient.connect(port,host){
      if(it.succeeded()){
        so = it.result() as NetSocketInternal
        val pipeline = so.channelHandlerContext().pipeline()
        pipeline.addFirst("aggregator", BinaryMemcacheObjectAggregator(Int.MAX_VALUE))
        pipeline.addFirst("memcached", BinaryMemcacheClientCodec())
        so.messageHandler(this::processResponse)
        future.complete(this)
      } else{
        future.fail(it.cause())
      }
    }
  }

  private fun processResponse(data:Any){
    val response = data as FullBinaryMemcacheResponse
    try {
      val handler: Handler<AsyncResult<FullBinaryMemcacheResponse>> = inflight.poll()
      // Handle the message
      handler.handle(Future.succeededFuture(response))
    } finally {
      response.release()
    }
  }

  private fun writeRequest(request: BinaryMemcacheRequest,completionHandler: Handler<AsyncResult<FullBinaryMemcacheResponse>>){
    so.writeMessage(request){
      if(it.succeeded()){
        inflight.add(completionHandler)
      }else{
        completionHandler.handle(Future.failedFuture(it.cause()))
      }
    }
  }

  override fun get(key: String, completionHandler: Handler<AsyncResult<Buffer>>) {
    val keyBuf = Unpooled.copiedBuffer(key, StandardCharsets.UTF_8)
    val request: FullBinaryMemcacheRequest = DefaultFullBinaryMemcacheRequest(keyBuf, Unpooled.EMPTY_BUFFER)
    request.setOpcode(BinaryMemcacheOpcodes.GET)
    writeRequest(request, Handler {
      if (it.succeeded()) {
        val response = it.result()
        when (val status: Short = response.status()) {
          0.toShort() -> {
            val value = Buffer.buffer(response.content())
            completionHandler.handle(Future.succeededFuture(value))
          }
          1.toShort() ->
            completionHandler.handle(Future.succeededFuture())
          else ->
            completionHandler.handle(Future.failedFuture(status.toString()))
        }
      } else {
        completionHandler.handle(Future.failedFuture(it.cause()))
      }
    })
  }

  override fun set(key: String, value: Buffer,completionHandler: Handler<AsyncResult<Void>>) {
    val keyBuf = Unpooled.copiedBuffer(key, StandardCharsets.UTF_8)
    val request: FullBinaryMemcacheRequest = DefaultFullBinaryMemcacheRequest(keyBuf
      , Unpooled.copiedBuffer(byteArrayOf(0xde.toByte(),0xad.toByte(),0xbe.toByte(),0xef.toByte(),0x00.toByte(),0x00.toByte(),0x1c.toByte(),0x20.toByte()))
      , value.byteBuf)
    request.setOpcode(BinaryMemcacheOpcodes.SET)
    writeRequest(request, Handler {
      if(it.succeeded()){
        completionHandler.handle(Future.succeededFuture())
      }else{
        completionHandler.handle(Future.failedFuture(it.cause()))
      }
    })
  }

  override fun delete(key: String) {
    val keyBuf = Unpooled.copiedBuffer(key, StandardCharsets.UTF_8)
    val request: FullBinaryMemcacheRequest = DefaultFullBinaryMemcacheRequest(keyBuf, Unpooled.EMPTY_BUFFER)
    request.setOpcode(BinaryMemcacheOpcodes.DELETE)
    writeRequest(request, Handler {  })
  }

  override fun close() {
    this.so.close()
    this.netClient.close()
  }

}
