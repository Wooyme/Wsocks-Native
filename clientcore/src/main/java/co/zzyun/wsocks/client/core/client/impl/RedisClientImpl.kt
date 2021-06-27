package co.zzyun.wsocks.client.core.client.impl

import co.zzyun.wsocks.client.core.KCP
import co.zzyun.wsocks.redis.RedisClient
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject

class RedisClientImpl(private val vertx: Vertx):IClientImpl {
  private lateinit var redisClient:RedisClient
  override fun stop() {
    redisClient.stop("offline")
  }

  override fun start(name: String, remoteHost: String, remotePort: Int, headers: JsonObject): Future<Void> {
    val fut = Future.future<Void>()
    redisClient  = RedisClient(vertx,remoteHost)
    redisClient.successHandler {
      println("Connection success")
      if(!fut.isComplete)
        fut.complete()
    }.shutdownHandler {
      if(!fut.isComplete){
        fut.fail(it)
      }
    }.start(remotePort.toString(),headers.getString("next"),headers)
    return fut
  }

  override fun connected(kcp: KCP) {
    redisClient.handler(Handler {
      kcp.InputAsync(it)
    })
  }

  override fun write(buffer: Buffer) {
    redisClient.write(buffer)
  }
}
