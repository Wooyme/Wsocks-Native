package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.data.RSAUtil
import co.zzyun.wsocks.server.sender.PcapSender
import co.zzyun.wsocks.server.sender.WebSocketSender
import io.vertx.core.http.HttpServer
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress

class WebSocketRecv:AbstractReceiver<ServerWebSocket>() {
  private val httpServer:HttpServer by lazy { vertx.createHttpServer() }
  override fun initServer(onConnect: (String,String,ServerWebSocket?, SocketAddress,Long,(KCP?)->Unit) -> Unit) {
    if(config().getJsonObject("pcap")!=null){
      senderMap["pcap"]=PcapSender(config().getJsonObject("pcap"))
    }
    senderMap["websocket"]=WebSocketSender()
    httpServer.websocketHandler { sock->
      val info = JsonObject(RSAUtil.decrypt(sock.headers()["info"]))
      val token = info.getString("token")
      val host = info.getString("host")
      val port = info.getInteger("port")
      val recvType = info.getString("recv")
      val conv = info.getLong("conv")
      println("Client:[$token] host:$host port:$port with conv $conv")
      if(host==null || port==null)
        onConnect(recvType,token,sock,sock.remoteAddress(),conv){
          if(it!=null){
            afterLogin(sock,it)
            println("Client:[$token]登录成功")
          }else{
            sock.reject(500)
          }
        }
      else
        onConnect(recvType,token,sock, SocketAddress.inetSocketAddress(port,host),conv){
          if(it!=null){
            afterLogin(sock,it)
            println("Client:[$token]登录成功")
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

  override fun close(conn: ServerWebSocket?, address: SocketAddress) {
    if(conn==null) throw RuntimeException("WebSocket cannot be null")
    conn.close()
  }
}
