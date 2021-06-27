package co.zzyun.wsocks

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
import java.net.Inet4Address
import java.net.InetAddress

object PcapUtil {

  private val debug = System.getProperty("ws.debug")?.toBoolean()?:false
  private const val MTU = 1400
  private lateinit var srcMacAddress: MacAddress
  private lateinit var gatewayMacAddress: MacAddress
  private lateinit var nif: PcapNetworkInterface
  private lateinit var sendHandle: PcapHandle
  fun initPcap(srcMacAddress: String, gatewayMacAddress: String) {
    PcapUtil.srcMacAddress = MacAddress.getByName(srcMacAddress, ":")
    PcapUtil.gatewayMacAddress = MacAddress.getByName(gatewayMacAddress, ":")
    nif = Pcaps.findAllDevs()[0]
    println(nif.name)
    sendHandle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10)
  }

  fun sendUdp(srcIpAddress: InetAddress, dstIpAddress: InetAddress, srcPort: Int, dstPort: Int, rawData: ByteArray) {
    if(debug){
      println("SendUdp: DataLen ${rawData.size}")
    }
    val udpBuilder = UdpPacket.Builder()
    udpBuilder.srcAddr(srcIpAddress)
      .dstAddr(dstIpAddress)
      .srcPort(UdpPort(srcPort.toShort(), "me"))
      .dstPort(UdpPort(dstPort.toShort(), "me"))
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
