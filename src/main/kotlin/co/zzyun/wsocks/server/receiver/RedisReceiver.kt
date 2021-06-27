package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.redis.RedisServer
import co.zzyun.wsocks.server.sender.RedisSender
import io.vertx.core.Handler
import io.vertx.core.net.SocketAddress

class RedisReceiver:AbstractLongConnectionReceiver<RedisServer.ConnectInfo>() {
  override fun initServer(handler: Handler<RedisServer.ConnectInfo>) {
    senderMap["redis"] = RedisSender()
    val server = RedisServer(vertx)
    server.onConnect(handler).start(id,host)
  }

  override fun handleLogin(conn: RedisServer.ConnectInfo): LoginInfo? {
    val token = conn.info.getString("token")
    val host = conn.info.getString("host")?:"0.0.0.0"
    val port = conn.info.getInteger("port")?:1000
    val recvType = conn.info.getString("recv")?:"memcached"
    val conv = conn.info.getLong("conv")
    return LoginInfo(SocketAddress.inetSocketAddress(port, host), recvType, token, conv)
  }

  override fun onConnected(conn: RedisServer.ConnectInfo, kcp: KCP) {
    conn.connect().handler(Handler {
      kcp.InputAsync(it)
    })
  }

  override fun onFailed(conn: RedisServer.ConnectInfo) {
    conn.reject()
  }

  override fun close(conn: RedisServer.ConnectInfo?, address: SocketAddress) {
    conn?.stop()
  }
}
