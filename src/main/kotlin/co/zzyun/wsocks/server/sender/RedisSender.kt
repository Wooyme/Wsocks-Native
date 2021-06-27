package co.zzyun.wsocks.server.sender

import co.zzyun.wsocks.redis.RedisServer
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.SocketAddress

class RedisSender:ISender {
  override fun send(conn: Any?, address: SocketAddress?, buffer: ByteArray, size: Int) {
    if(conn !is RedisServer.ConnectInfo) throw RuntimeException("Need RedisServer.ConnectInfo when using MemcachedSender")
    try {
      conn.write(Buffer.buffer().appendBytes(buffer, 0, size))
    }catch(ignored:Throwable){ }
  }

  override fun close(conn: Any?, address: SocketAddress?) {
    if(conn !is RedisServer.ConnectInfo) throw RuntimeException("Need RedisServer.ConnectInfo when using MemcachedSender")
    try {
      conn.stop()
    }catch (ignored:Throwable){}
  }
}
