package co.zzyun.wsocks.server

import co.zzyun.wsocks.PcapUtil
import co.zzyun.wsocks.unitMap
import io.vertx.core.buffer.Buffer
import org.pcap4j.core.BpfProgram
import org.pcap4j.core.PacketListener
import org.pcap4j.core.PcapNetworkInterface
import org.pcap4j.packet.*
import org.pcap4j.packet.namednumber.EtherType
import org.pcap4j.packet.namednumber.IpNumber
import org.pcap4j.packet.namednumber.IpVersion
import org.pcap4j.packet.namednumber.UdpPort
import org.pcap4j.util.IpV4Helper
import java.net.Inet4Address
import java.net.InetAddress
import java.util.*

class PcapUdp:BaseServer() {

  companion object {
      private val debug = System.getProperty("pcap.debug")?.toBoolean()?:false
  }
  private val conMap = HashMap<String,Long>()
  override fun onLogin(ip:String,port:Int,conv:Long){
    conMap["$ip:$port"] = conv
    println("Login $ip:$port,$conv")
  }
  private val fakePacket by lazy {
    val udpBuilder = UdpPacket.Builder()
    udpBuilder.srcAddr(Inet4Address.getByName(srcIp))
      .dstAddr(Inet4Address.getByName("8.8.8.8"))
      .srcPort(UdpPort((srcPort+67).toShort(), "me"))
      .dstPort(UdpPort.DOMAIN)
      .correctLengthAtBuild(true)
      .correctChecksumAtBuild(true)
    val ipV4Builder = IpV4Packet.Builder()
      .version(IpVersion.IPV4)
      .tos(IpV4Rfc791Tos.newInstance(0.toByte()))
      .ttl(100.toByte())
      .protocol(IpNumber.UDP)
      .srcAddr(Inet4Address.getByName(srcIp) as Inet4Address)
      .dstAddr(Inet4Address.getByName("8.8.8.8") as Inet4Address)
      .payloadBuilder(udpBuilder)
      .correctChecksumAtBuild(true)
      .correctLengthAtBuild(true)

    val etherBuilder = EthernetPacket.Builder()
    etherBuilder
      .dstAddr(PcapUtil.gatewayMacAddress)
      .srcAddr(PcapUtil.srcMacAddress)
      .type(EtherType.IPV4)
      .paddingAtBuild(true)
    val ipV4Packet = IpV4Helper.fragment(ipV4Builder.build(), 1200)[0]
    etherBuilder.payloadBuilder(
      object : AbstractPacket.AbstractBuilder() {
        override fun build(): Packet {
          return ipV4Packet
        }
      }).build()
  }
  override fun initServer() {
    val handle = PcapUtil.nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10)
    handle.setFilter("dst port $srcPort", BpfProgram.BpfCompileMode.OPTIMIZE)
    val listener = PacketListener {
      val ethernetPacket = EthernetPacket.newPacket(it.rawData, 0, it.length())
      if(ethernetPacket.header.type== EtherType.IPV4) {
        try {
          val ipPacket = ethernetPacket.get(IpV4Packet::class.java)

          if(ipPacket.header.protocol == IpNumber.UDP){
            val udpPacket = ipPacket.get(UdpPacket::class.java)

            if(udpPacket.header.dstPort.value()!=srcPort.toShort()) return@PacketListener
            if(udpPacket.payload!=null && udpPacket.payload.length()<4) return@PacketListener
            val hostAddress = ipPacket.header.srcAddr.hostAddress
            val hostSrcPort = udpPacket.header.srcPort.value()
            if(debug){
              println("[Pcap] Src ${ipPacket.header.srcAddr}:${udpPacket.header.srcPort}" +
                ", Dst ${ipPacket.header.dstAddr}:${udpPacket.header.dstPort}, Len ${ipPacket.payload.rawData.size}")
            }
            unitMap[conMap["$hostAddress:$hostSrcPort"]]?.kcp?.input(Buffer.buffer(udpPacket.payload.rawData))?:run{
              println("$hostAddress:$hostSrcPort,Not Found!")
            }
          }
        }catch (e:Throwable){
          e.printStackTrace()
        }
      }
    }
    Thread {
      try {
        handle.loop(-1, listener)
      }catch (e:Throwable){
        e.printStackTrace()
      }
    }.start()
    vertx.setPeriodic(10){
      fakeMac()
    }
  }

  private fun fakeMac(){
    PcapUtil.sendHandle.sendPacket(fakePacket)
  }

  override fun send(srcIp: InetAddress, dstIp: InetAddress, srcPort: Short, dstPort: Short, buffer: ByteArray, size: Int) {
    PcapUtil.sendUdp(srcIp,dstIp,srcPort,dstPort, Arrays.copyOfRange(buffer,0,size))
  }
}
