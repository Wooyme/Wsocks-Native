package co.zzyun.wsocks.server.sender

import io.vertx.core.net.SocketAddress

interface ISender {
  fun send(conn: Any?,address:SocketAddress?, buffer: ByteArray, size: Int)
  fun close(conn: Any?,address: SocketAddress?)
}
