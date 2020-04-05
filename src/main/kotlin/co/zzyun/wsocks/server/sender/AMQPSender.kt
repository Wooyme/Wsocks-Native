package co.zzyun.wsocks.server.sender

import io.vertx.core.buffer.Buffer
import io.vertx.core.net.SocketAddress
import io.vertx.ext.amqp.AmqpMessage
import io.vertx.ext.amqp.AmqpSender

class AMQPSender:ISender {
  override fun send(conn: Any?, address: SocketAddress?, buffer: ByteArray, size: Int) {
    if(conn !is Pair<*, *> || conn.first !is AmqpSender) throw RuntimeException("Need ServerWebSocket when using WebSocketSender")
    try {
      (conn.first as AmqpSender).send(AmqpMessage.create().withBufferAsBody(Buffer.buffer().appendBytes(buffer,0,size)).build())
    }catch(ignored:Throwable){ }
  }

  override fun close(conn: Any?, address: SocketAddress?) {
    if(conn !is Pair<*, *> || conn.first !is AmqpSender) throw RuntimeException("Need ServerWebSocket when using WebSocketSender")
    try {
      (conn.first as AmqpSender).close {}
    }catch(ignored:Throwable){ }
  }
}
