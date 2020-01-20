package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.server.sender.PcapSender
import io.vertx.core.buffer.Buffer
import io.vertx.core.datagram.DatagramSocket
import io.vertx.core.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap

class UdpRecv:AbstractReceiver<Void>() {
  private val udpServer:DatagramSocket by lazy { vertx.createDatagramSocket() }
  private val conMap:MutableMap<String,KCP> = ConcurrentHashMap()
  override fun initServer(onConnect: (String, String, Void?, SocketAddress, Long, (KCP?) -> Unit) -> Unit) {
    senderMap["pcap"]= PcapSender(config().getJsonObject("pcap"))
    udpServer.handler {
      val remote = it.sender().host()+":"+it.sender().port()
      conMap[remote]?.Send(it.data().bytes)?:let{_->
        val info = try{
          it.data().toJsonObject()
        }catch (ignored:Throwable){
          return@handler
        }
        val token = info.getString("token")
        val conv = info.getLong("conv")
        val host = it.sender().host()
        val port = it.sender().port()
        println("Client:[$token] host:$host port:$port with conv $conv")
        onConnect("pcap",token,null, SocketAddress.inetSocketAddress(port,host),conv){kcp->
          if(kcp!=null){
            conMap[remote] = kcp
          }else{
            return@onConnect
          }
        }
      }
    }.listen(loginPort,"0.0.0.0"){
      if(it.succeeded())
        println("UdpServer Listen at $loginPort")
      else
        it.cause().printStackTrace()
    }
  }

  override fun close(conn: Void?, address: SocketAddress) {
    conMap.remove(address.host()+":"+address.port())
  }
}
