package co.zzyun.wsocks.server.sender

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.net.SocketAddress

class WebSocketSender:ISender {
  override fun send(conn: Any?, address: SocketAddress?, buffer: ByteArray, size: Int) {
    if(conn !is ServerWebSocket) throw RuntimeException("Need ServerWebSocket when using WebSocketSender")
    conn.writeBinaryMessage(Buffer.buffer().appendBytes(buffer,0,size))
  }

  override fun close(conn: Any?, address: SocketAddress?) {
    if(conn !is ServerWebSocket) throw RuntimeException("Need ServerWebSocket when using WebSocketSender")
    conn.close()
  }
}
