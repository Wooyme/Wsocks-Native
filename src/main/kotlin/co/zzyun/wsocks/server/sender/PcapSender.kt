package co.zzyun.wsocks.server.sender

import co.zzyun.wsocks.PcapJNI
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress

class PcapSender(private val config:JsonObject):ISender {
  private val realIp by lazy { config.getString("dev") }
  private val srcIp by lazy { config.getString("ip") }
  private val srcPort by lazy { config.getInteger("port") }
  private val srcMac by lazy { config.getString("srcMac") }
  private val gatewayMac by lazy { config.getString("gatewayMac") }
  private val ptr = PcapJNI.initPcap(realIp,srcMac,gatewayMac)

  override fun send(conn: Any?, address: SocketAddress?, buffer: ByteArray, size: Int) {
    if(address==null) throw RuntimeException("Address can not be null")
    PcapJNI.sendUdp(ptr,srcIp,address.host(),srcPort,address.port(),buffer,size)
  }

  override fun close(conn: Any?, address: SocketAddress?) {

  }
}
