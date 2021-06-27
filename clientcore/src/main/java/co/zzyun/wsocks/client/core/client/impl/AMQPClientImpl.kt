package co.zzyun.wsocks.client.core.client.impl

import co.zzyun.wsocks.client.core.KCP
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.amqp.AmqpReceiver
import io.vertx.ext.amqp.AmqpSender

class AMQPClientImpl : IClientImpl{
  private lateinit var sender: AmqpSender
  private lateinit var receiver: AmqpReceiver
  override fun stop() {
    TODO("Not yet implemented")
  }

  override fun start(name: String, remoteHost: String, remotePort: Int, headers: JsonObject): Future<Void> {
    TODO("Not yet implemented")
  }

  override fun connected(kcp: KCP) {
    TODO("Not yet implemented")
  }

  override fun write(buffer: Buffer) {
    TODO("Not yet implemented")
  }

}
