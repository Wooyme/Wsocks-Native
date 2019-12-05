package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.data.RSAUtil
import co.zzyun.wsocks.server.sender.PcapSender
import co.zzyun.wsocks.server.sender.RawUdpSender
import co.zzyun.wsocks.server.sender.TcpSender
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetSocket
import io.vertx.core.net.SocketAddress
import java.lang.RuntimeException

class TcpRecv:AbstractReceiver<NetSocket>() {
  private val tcpServer by lazy { vertx.createNetServer() }
  override val type: String get() = "tcp"

  override fun initServer(onConnect: (String, String, NetSocket?, SocketAddress?, (KCP?) -> Unit) -> Unit) {
    config().getJsonObject("sender").forEach {
      when(it.key){
        "raw"->senderMap["raw"]= RawUdpSender(config().getJsonObject("sender").getJsonObject("raw"))
        "pcap"->senderMap["pcap"]= PcapSender(config().getJsonObject("sender").getJsonObject("pcap"))
        "tcp"->senderMap["tcp"] = TcpSender()
      }
    }
    val doLogin = { buf:Buffer,ns:NetSocket->
      val info = JsonObject(RSAUtil.decrypt(buf.getString(0,buf.length())))
      val token = info.getString("token")
      val host = info.getString("host")
      val port = info.getInteger("port")
      val recvType = info.getString("recv")
      if(host==null || port==null)
        onConnect(recvType,token,ns,null){kcp->
          if(kcp!=null){
            ns.handler {
              kcp.InputAsync(it)
            }
          }else{
            ns.close()
          }
        }
      else
        onConnect(recvType,token,ns, SocketAddress.inetSocketAddress(port,host)){kcp->
          if(kcp!=null){
            ns.handler {
              kcp.InputAsync(it)
            }
          }else{
            ns.close()
          }
        }
    }
    tcpServer.connectHandler { ns->
      var len = 0
      val buffer = Buffer.buffer()
      ns.handler {
        if(len==0)
          len = it.getInt(0)
        if(len == it.length()-2){
          doLogin(it.getBuffer(2,it.length()),ns)
        }else{
          buffer.appendBuffer(it.getBuffer(2,it.length()))
          if(len==buffer.length()){
            doLogin(buffer,ns)
          }
        }
      }
    }.listen(loginPort) {
      if(it.succeeded())
        println("Tcp Server listening at $loginPort")
      else
        it.cause().printStackTrace()
    }
  }

  override fun close(conn: NetSocket?, address: SocketAddress?) {
    if(conn==null) throw RuntimeException("NetSocket cannot be null")
    conn.close()
  }
}
