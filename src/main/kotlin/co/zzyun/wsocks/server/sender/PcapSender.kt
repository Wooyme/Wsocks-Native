package co.zzyun.wsocks.server.sender

import co.zzyun.wsocks.PcapUtil
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress
import java.net.Inet4Address

class PcapSender(private val config:JsonObject):ISender {
  private val srcIp by lazy { Inet4Address.getByName(config.getString("ip")) }
  private val srcPort by lazy { config.getInteger("port") }
  private val srcMac by lazy { config.getString("srcMac") }
  private val gatewayMac by lazy { config.getString("gatewayMac") }
  init{
    PcapUtil.initPcap(srcMac,gatewayMac)
  }
  override fun send(conn: Any?, address: SocketAddress?, buffer: ByteArray, size: Int) {
    if(address==null) throw RuntimeException("Address can not be null")
    PcapUtil.sendUdp(srcIp,Inet4Address.getByName(address.host()),srcPort,address.port(),buffer)
  }

  override fun close(conn: Any?, address: SocketAddress?) {

  }
}
