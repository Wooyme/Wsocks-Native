package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.memcached.MemcachedWayServer
import co.zzyun.wsocks.server.sender.MemcachedSender
import io.vertx.core.Handler
import io.vertx.core.net.SocketAddress

class MemcachedReceiver:AbstractLongConnectionReceiver<MemcachedWayServer.ConnectInfo>(){
  override fun initServer(handler: Handler<MemcachedWayServer.ConnectInfo>) {
    senderMap["memcached"] = MemcachedSender()
    val server = MemcachedWayServer(vertx)
    server.onConnect(handler).start(id,host)
  }

  override fun handleLogin(conn: MemcachedWayServer.ConnectInfo): LoginInfo? {
    val token = conn.info.getString("token")
    val host = conn.info.getString("host")?:"0.0.0.0"
    val port = conn.info.getInteger("port")?:1000
    val recvType = conn.info.getString("recv")?:"memcached"
    val conv = conn.info.getLong("conv")
    return LoginInfo(SocketAddress.inetSocketAddress(port, host), recvType, token, conv)
  }

  override fun onConnected(conn: MemcachedWayServer.ConnectInfo, kcp: KCP) {
    conn.connect().setHandler {
      if(it.succeeded()){
        it.result().handler(Handler {
          kcp.InputAsync(it)
        })
      }else{
        it.cause().printStackTrace()
      }
    }
  }

  override fun onFailed(conn: MemcachedWayServer.ConnectInfo) {
    conn.reject()
  }

  override fun close(conn: MemcachedWayServer.ConnectInfo?, address: SocketAddress) {
    conn?.stop()
  }

}
