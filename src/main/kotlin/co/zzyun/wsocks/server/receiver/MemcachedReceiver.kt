package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.memcached.MemcachedConnection
import co.zzyun.wsocks.memcached.MemcachedServer
import co.zzyun.wsocks.server.sender.MemcachedSender
import io.vertx.core.Handler
import io.vertx.core.net.SocketAddress

class MemcachedReceiver:AbstractLongConnectionReceiver<MemcachedServer.ConnectInfo>(){
  override fun initServer(handler: Handler<MemcachedServer.ConnectInfo>) {
    senderMap["memcached"] = MemcachedSender()
    val server = MemcachedServer(vertx)
    server.onConnect(handler).start(id,host)
  }

  override fun handleLogin(conn: MemcachedServer.ConnectInfo): LoginInfo? {
    val token = conn.info.getString("token")
    val host = conn.info.getString("host")?:"0.0.0.0"
    val port = conn.info.getInteger("port")?:1000
    val recvType = conn.info.getString("recv")?:"memcached"
    val conv = conn.info.getLong("conv")
    return LoginInfo(SocketAddress.inetSocketAddress(port, host), recvType, token, conv)
  }

  override fun onConnected(conn: MemcachedServer.ConnectInfo, kcp: KCP) {
    conn.connect().handler(Handler {
      kcp.InputAsync(it)
    })
  }

  override fun onFailed(conn: MemcachedServer.ConnectInfo) {
    conn.reject()
  }

  override fun close(conn: MemcachedServer.ConnectInfo?, address: SocketAddress) {
    conn?.stop()
  }

}
