package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.server.sender.PcapSender
import co.zzyun.wsocks.server.sender.WebSocketSender
import io.vertx.core.Handler
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress
import java.util.*

class WebSocketRecv : AbstractLongConnectionReceiver<ServerWebSocket>() {
  private val httpServer: HttpServer by lazy { vertx.createHttpServer(HttpServerOptions().setTcpNoDelay(true)) }
  override fun initServer(handler: Handler<ServerWebSocket>) {
    if (config().getJsonObject("pcap") != null) {
      senderMap["pcap"] = PcapSender(config().getJsonObject("pcap"))
    }
    senderMap["websocket"] = WebSocketSender()
    httpServer.websocketHandler(handler).listen(loginPort) {
      if (it.succeeded())
        println("WebSocket Server listening at $loginPort")
      else
        it.cause().printStackTrace()
    }
  }

  override fun handleLogin(conn: ServerWebSocket): LoginInfo? {
    val info = JsonObject(String(Base64.getDecoder().decode(conn.headers()["info"])))
    val token = info.getString("token")
    val host = info.getString("host")
    val port = info.getInteger("port")
    val recvType = info.getString("recv")
    val conv = info.getLong("conv")
    return if(host==null || port==null){
      LoginInfo(conn.remoteAddress(),recvType, token, conv)
    }else{
      LoginInfo(SocketAddress.inetSocketAddress(port,host),recvType, token, conv)
    }
  }

  override fun onConnected(conn: ServerWebSocket, kcp: KCP) {
    conn.binaryMessageHandler {
      kcp.InputAsync(it)
    }
    conn.accept()
  }

  override fun onFailed(conn: ServerWebSocket) {
    conn.reject(500)
  }

  override fun close(conn: ServerWebSocket?, address: SocketAddress) {
    if (conn == null) throw RuntimeException("WebSocket cannot be null")
    try {
      conn.close()
    } catch (ignored: Throwable) {
    }
  }
}
