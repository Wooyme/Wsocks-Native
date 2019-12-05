package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.data.RSAUtil
import co.zzyun.wsocks.server.sender.PcapSender
import co.zzyun.wsocks.server.sender.RawUdpSender
import co.zzyun.wsocks.server.sender.WebSocketSender
import io.vertx.core.http.HttpServer
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress

class WebSocketRecv:AbstractReceiver<ServerWebSocket>() {
  private val httpServer:HttpServer by lazy { vertx.createHttpServer() }
  override val type: String get() = "websocket"

  override fun initServer(onConnect: (String,String,ServerWebSocket?, SocketAddress?,(KCP?)->Unit) -> Unit) {
    config().getJsonObject("sender").forEach {
      when(it.key){
        "raw"->senderMap["raw"]=RawUdpSender(config().getJsonObject("sender").getJsonObject("raw"))
        "pcap"->senderMap["pcap"]=PcapSender(config().getJsonObject("sender").getJsonObject("pcap"))
        "websocket"->senderMap["websocket"]=WebSocketSender()
      }
    }
    httpServer.websocketHandler { sock->
      val info = JsonObject(RSAUtil.decrypt(sock.headers()["info"]))
      val token = info.getString("token")
      val host = info.getString("host")
      val port = info.getInteger("port")
      val recvType = info.getString("recv")
      if(host==null || port==null)
        onConnect(recvType,token,sock,null){
          if(it!=null){
            afterLogin(sock,it)
          }else{
            sock.reject(500)
          }
        }
      else
        onConnect(recvType,token,sock, SocketAddress.inetSocketAddress(port,host)){
          if(it!=null){
            afterLogin(sock,it)
          }else{
            sock.reject(500)
          }
        }
    }.listen(loginPort){
      if(it.succeeded())
        println("WebSocket Server listening at $loginPort")
      else
        it.cause().printStackTrace()
    }
  }

  private fun afterLogin(sock:ServerWebSocket, kcp:KCP) {
    sock.binaryMessageHandler {
      kcp.InputAsync(it)
    }
    sock.accept()
  }

  override fun close(conn: ServerWebSocket?, address: SocketAddress?) {
    if(conn==null) throw RuntimeException("WebSocket cannot be null")
    conn.close()
  }
}
