package co.zzyun.wsocks.server.sender

import io.vertx.core.buffer.Buffer
import io.vertx.core.net.NetSocket
import io.vertx.core.net.SocketAddress

class TcpSender:ISender {
  override fun send(conn: Any?, address: SocketAddress?, buffer: ByteArray, size: Int) {
    if(conn !is NetSocket) throw RuntimeException("Need NetSocket when using WebSocketSender")
    conn.write(Buffer.buffer().appendBytes(buffer,0,size))
  }

  override fun close(conn: Any?, address: SocketAddress?) {
    if(conn !is NetSocket) throw RuntimeException("Need ServerWebSocket when using WebSocketSender")
    conn.close()
  }
}
