package co.zzyun.wsocks

import co.zzyun.wsocks.data.UserInfo
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import org.pcap4j.core.NotOpenException
import org.pcap4j.core.PcapHandle
import org.pcap4j.core.PcapNativeException
import org.pcap4j.core.PcapNetworkInterface
import org.pcap4j.packet.EthernetPacket
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.UdpPacket
import org.pcap4j.packet.UnknownPacket
import org.pcap4j.packet.namednumber.EtherType
import org.pcap4j.packet.namednumber.IpNumber
import org.pcap4j.packet.namednumber.IpVersion
import org.pcap4j.packet.namednumber.UdpPort
import org.pcap4j.util.NifSelector
import java.io.File
import java.net.Inet4Address
import java.net.UnknownHostException
import java.util.*


fun main(args: Array<String>) {
  val serverConfig = JsonObject(File(args[0]).readText())
  Vertx.vertx().deployVerticle(MainVerticle(),DeploymentOptions().setConfig(serverConfig))
}

class MainVerticle: AbstractVerticle() {
  companion object {
    private const val READ_TIMEOUT = 10
    private const val SNAPLEN = 65536
  }
  private var sendHandle: PcapHandle
  private val srcIp:Inet4Address by lazy { Inet4Address.getByName(config().getString("host")) as Inet4Address }
  private val userMap = HashMap<String, UserInfo>()
  private val srcPort by lazy { config().getInteger("port").toShort() }
  private val maxWaitSnd by lazy { config().getInteger("maxWaitSnd") }
  init{
    val nif = NifSelector().selectNetworkInterface()
    sendHandle = nif.openLive(SNAPLEN, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, READ_TIMEOUT)
  }

  private fun initParams() {
    config().getJsonArray("users").forEach { v ->
      val userInfo = UserInfo.fromJson(v as JsonObject)
      userMap[userInfo.secret] = userInfo
    }
  }

  override fun start(startFuture: Future<Void>) {
    initParams()
    vertx.createHttpServer().requestHandler { req ->
      if (req.getParam("c") == null || req.getParam("p") == null || req.getParam("s") == null) {
        req.response().setStatusCode(404).end()
        return@requestHandler
      }
      val user = this.userMap[req.getParam("s")]?:return@requestHandler req.response().setStatusCode(300).end()
      val dstPort = req.getParam("p").toShort()
      val dstIp = try {
        Inet4Address.getByName(req.netSocket().remoteAddress().host()) as Inet4Address
      } catch (e: UnknownHostException) {
        e.printStackTrace()
        req.response().setStatusCode(500).end()
        return@requestHandler
      }
      val conv = try {
        req.getParam("c").toLong()
      } catch (e: NumberFormatException) {
        req.response().setStatusCode(500).end()
        return@requestHandler
      }
      val kcp = object : KCP(conv) {
        override fun output(buffer: ByteArray, size: Int) {
          try {
            sendUDP(dstIp, srcIp, srcPort, dstPort, Arrays.copyOfRange(buffer, 0, size))
          } catch (e: Exception) {
            e.printStackTrace()
          }
        }
      }

      kcp.SetMtu(1000)
      kcp.WndSize(128, 128)
      kcp.NoDelay(1, 10, 2, 1)
      req.bodyHandler {
        buf -> kcp.Input(buf.bytes)
      }
      vertx.deployVerticle(TransportUnit(req,kcp,user,maxWaitSnd), DeploymentOptions().setConfig(config()))
    }.listen(config().getInteger("port")) { http ->
      if (http.succeeded()) {
        startFuture.complete()
        println("HTTP server started on port ${config().getInteger("port")}")
      } else {
        startFuture.fail(http.cause())
      }
    }
  }


  private fun sendUDP(dstIp: Inet4Address, srcIp: Inet4Address, dstPort: Short, srcPort: Short, bytes: ByteArray) {
    val udpBuilder = UdpPacket.Builder()
      .srcPort(UdpPort(srcPort, "WSC"))
      .dstPort(UdpPort(dstPort, "WSC"))
      .payloadBuilder(UnknownPacket.Builder().rawData(bytes))
    val ipBuilder = IpV4Packet.Builder()
      .version(IpVersion.IPV4)
      //      .tos(IpV4Rfc791Tos.newInstance((byte) 0))
      .ttl(100.toByte())
      .protocol(IpNumber.UDP)
      .dstAddr(dstIp)
      .srcAddr(srcIp)
      .payloadBuilder(udpBuilder)
      .correctChecksumAtBuild(true)
      .correctLengthAtBuild(true)
    val etherBuilder = EthernetPacket.Builder()
      .type(EtherType.IPV4)
      .paddingAtBuild(true)
      .payloadBuilder(ipBuilder)
    try {
      sendHandle.sendPacket(etherBuilder.build())
    } catch (e: PcapNativeException) {
      e.printStackTrace()
    } catch (e: NotOpenException) {
      e.printStackTrace()
    }
  }
}
