package co.zzyun.wsocks.server.sender

import co.zzyun.wsocks.memcached.MemcachedDistributedConnection
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.SocketAddress

class MemcachedDistributedSender :ISender {
  override fun send(conn: Any?, address: SocketAddress?, buffer: ByteArray, size: Int) {
    if(conn !is MemcachedDistributedConnection) throw RuntimeException("Need MemcachedDistributedConnection when using MemcachedDistributedSender")
    try{
      conn.write(Buffer.buffer().appendBytes(buffer, 0, size))
    }catch(ignored:Throwable){ }
  }

  override fun close(conn: Any?, address: SocketAddress?) {
    if(conn !is MemcachedDistributedConnection) throw RuntimeException("Need MemcachedDistributedConnection when using MemcachedDistributedSender")
    try{
      conn.stop()
    }catch(ignored:Throwable){ }
  }

}
