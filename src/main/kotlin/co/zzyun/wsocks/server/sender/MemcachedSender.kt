package co.zzyun.wsocks.server.sender

import co.zzyun.wsocks.memcached.MemcachedConnection
import co.zzyun.wsocks.memcached.MemcachedServer
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.SocketAddress

class MemcachedSender:ISender{
  override fun send(conn: Any?, address: SocketAddress?, buffer: ByteArray, size: Int) {
    if(conn !is MemcachedServer.ConnectInfo) throw RuntimeException("Need SMemcachedServer.ConnectInfo when using MemcachedSender")
    try {
      conn.write(Buffer.buffer().appendBytes(buffer, 0, size))
    }catch(ignored:Throwable){ }
  }

  override fun close(conn: Any?, address: SocketAddress?) {
    if(conn !is MemcachedServer.ConnectInfo) throw RuntimeException("Need MemcachedServer.ConnectInfo when using MemcachedSender")
    try {
      conn.stop()
    }catch (ignored:Throwable){}
  }

}
