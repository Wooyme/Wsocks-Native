package co.zzyun.wsocks

import org.pcap4j.core.PacketListener
import org.pcap4j.core.PcapHandle
import org.pcap4j.core.PcapNetworkInterface
import org.pcap4j.core.Pcaps
import org.pcap4j.packet.*
import org.pcap4j.packet.namednumber.EtherType
import org.pcap4j.packet.namednumber.IpNumber
import org.pcap4j.packet.namednumber.IpVersion
import org.pcap4j.packet.namednumber.UdpPort
import org.pcap4j.util.IpV4Helper
import org.pcap4j.util.MacAddress
import org.pcap4j.util.NifSelector
import java.net.Inet4Address
import java.net.InetAddress

object PcapUtil {

  private val debug = System.getProperty("ws.debug")?.toBoolean()?:false
  private const val MTU = 1400
  lateinit var srcMacAddress: MacAddress
  lateinit var gatewayMacAddress: MacAddress
  lateinit var nif: PcapNetworkInterface
  lateinit var sendHandle: PcapHandle
  fun initPcap(srcMacAddress: String, gatewayMacAddress: String) {
    PcapUtil.srcMacAddress = MacAddress.getByName(srcMacAddress, ":")
    PcapUtil.gatewayMacAddress = MacAddress.getByName(gatewayMacAddress, ":")
    nif = Pcaps.findAllDevs()[0]
    println(nif.name)
    PcapUtil.sendHandle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10)
    //enableSniffer()
  }

  private fun enableSniffer(){
    val handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10)
    val listener = PacketListener {
      val ethernetPacket = EthernetPacket.newPacket(it.rawData, 0, it.length())
      if(ethernetPacket.header.type== EtherType.IPV4) {
        try {
          val ipPacket = ethernetPacket.get(IpV4Packet::class.java)
          if(System.getProperty("sniffer.debug")?.toBoolean()==true)
            println("[Sniffer][${ethernetPacket.header.type.name()}] SrcMac:${ethernetPacket.header.srcAddr} DstMac:${ethernetPacket.header.dstAddr} SrcIp:${ipPacket.header.srcAddr} DstIp:${ipPacket.header.dstAddr}")
          if(ipPacket.header.protocol == IpNumber.UDP){
            val udpPacket = ipPacket.get(UdpPacket::class.java)

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
  }

  fun sendUdp(srcIpAddress: InetAddress, dstIpAddress: InetAddress, srcPort: Short, dstPort: Short, rawData: ByteArray) {
    if(debug){
      println("SendUdp: DataLen ${rawData.size}")
    }
    val udpBuilder = UdpPacket.Builder()
    udpBuilder.srcAddr(srcIpAddress)
      .dstAddr(dstIpAddress)
      .srcPort(UdpPort(srcPort, "me"))
      .dstPort(UdpPort(dstPort, "me"))
      .payloadBuilder(UnknownPacket.Builder().rawData(rawData))
      .correctLengthAtBuild(true)
      .correctChecksumAtBuild(true)
    val ipV4Builder = IpV4Packet.Builder()
        .version(IpVersion.IPV4)
        .tos(IpV4Rfc791Tos.newInstance(0.toByte()))
        .ttl(100.toByte())
        .protocol(IpNumber.UDP)
        .srcAddr(srcIpAddress as Inet4Address)
        .dstAddr(dstIpAddress as Inet4Address)
        .payloadBuilder(udpBuilder)
        .correctChecksumAtBuild(true)
        .correctLengthAtBuild(true)

    val etherBuilder = EthernetPacket.Builder()
    etherBuilder
      .dstAddr(gatewayMacAddress)
      .srcAddr(srcMacAddress)
      .type(EtherType.IPV4)
      .paddingAtBuild(true)
    for (ipV4Packet in IpV4Helper.fragment(ipV4Builder.build(), MTU)) {
      etherBuilder.payloadBuilder(
        object : AbstractPacket.AbstractBuilder() {
          override fun build(): Packet {
            return ipV4Packet
          }
        })
      sendHandle.sendPacket(etherBuilder.build())
    }
  }
}
