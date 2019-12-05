package co.zzyun.wsocks.server.sender

import co.zzyun.wsocks.UdpUtil
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress
import java.lang.RuntimeException

class RawUdpSender(private val config:JsonObject):ISender {
  private val srcIp by lazy { config.getString("ip") }
  private val srcPort by lazy { config.getInteger("port") }
  private val rawMap = HashMap<SocketAddress,Long>()
  override fun close(conn: Any?, address: SocketAddress?) {
    rawMap.remove(address)
  }
  override fun send(conn: Any?, address: SocketAddress?, buffer: ByteArray, size: Int) {
    if(address==null)throw RuntimeException("address can not be null")
    (rawMap[address]?:let{
      val ptr = UdpUtil.initRaw(srcIp,srcPort,address.host(),address.port())
      rawMap[address] = ptr
      ptr
    }).let {
      UdpUtil.sendUdp(it,buffer,size)
    }
  }
}
