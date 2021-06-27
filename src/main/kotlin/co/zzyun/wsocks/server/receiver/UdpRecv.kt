package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.server.sender.PcapSender
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.datagram.DatagramPacket
import io.vertx.core.datagram.DatagramSocket
import io.vertx.core.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap

class UdpRecv:AbstractReceiver<DatagramPacket>() {
  private val udpServer:DatagramSocket by lazy { vertx.createDatagramSocket() }
  private val conMap:MutableMap<String,KCP> = ConcurrentHashMap()
  override fun initServer(handler: Handler<DatagramPacket>) {
    senderMap["pcap"]= PcapSender(config().getJsonObject("pcap"))
    udpServer.handler(handler).listen(loginPort,"0.0.0.0"){
      if(it.succeeded())
        println("UdpServer Listen at $loginPort")
      else
        it.cause().printStackTrace()
    }
  }

  override fun handleLogin(conn: DatagramPacket): LoginInfo? {
    return try{
      val json = conn.data().toJsonObject()
      val token = json.getString("token")
      val conv = json.getLong("conv")
      LoginInfo(conn.sender(),"pcap",token, conv)
    }catch (ignored:Throwable){
      null
    }
  }

  override fun getConnection(conn: DatagramPacket): SocketAddress {
    return conn.sender()
  }

  override fun onData(conn: DatagramPacket, kcp: KCP) {
    kcp.Send(conn.data().bytes)
  }

  override fun onConnected(conn: DatagramPacket, kcp: KCP) {
    return
  }

  override fun onFailed(conn: DatagramPacket) {
    return
  }

  override fun close(conn: DatagramPacket?, address: SocketAddress) {
    conMap.remove(address.host()+":"+address.port())
  }
}
