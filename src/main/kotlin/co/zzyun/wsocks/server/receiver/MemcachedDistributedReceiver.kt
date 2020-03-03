package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.memcached.MemcachedDistributedConnection
import co.zzyun.wsocks.memcached.MemcachedDistributedServer
import io.vertx.core.Handler
import io.vertx.core.net.SocketAddress

class MemcachedDistributedReceiver:AbstractLongConnectionReceiver<MemcachedDistributedConnection>() {
  override fun initServer(handler: Handler<MemcachedDistributedConnection>) {
    val server = MemcachedDistributedServer(vertx,host,id)
    server.handler(handler).start()
  }

  override fun handleLogin(conn: MemcachedDistributedConnection): LoginInfo? {
    val token = conn.info.getString("token")
    val host = conn.info.getString("host")?:"0.0.0.0"
    val port = conn.info.getInteger("port")?:1000
    val recvType = conn.info.getString("recv")?:"memcached"
    val conv = conn.info.getLong("conv")
    return LoginInfo(SocketAddress.inetSocketAddress(port, host), recvType, token, conv)
  }

  override fun onConnected(conn: MemcachedDistributedConnection, kcp: KCP) {
    conn.handler(Handler {
      kcp.InputAsync(it)
    }).connect()
  }

  override fun onFailed(conn: MemcachedDistributedConnection) {
    conn.reject()
  }

  override fun close(conn: MemcachedDistributedConnection?, address: SocketAddress) {
    conn?.stop()
  }
}
