package co.zzyun.wsocks.server

import co.zzyun.wsocks.SimpleUdp
import co.zzyun.wsocks.unitMap
import io.vertx.core.buffer.Buffer
import java.net.InetAddress
import java.util.*

class FullUdp:BaseServer() {
  private val udpServer by lazy { SimpleUdp(srcPort) }
  private val conMap = HashMap<String,Long>()
  private val lastAccessMap = HashMap<Long,Long>()
  override fun onLogin(ip:String,port:Int,conv:Long){
    vertx.setPeriodic(60*1000){
      if(Date().time-(lastAccessMap[conv]?:0)>60*1000){
        lastAccessMap.remove(conv)
        conMap.remove("$ip:$port")
        vertx.cancelTimer(it)
        unitMap[conv]?.deploymentID()?.let(vertx::undeploy)
      }
    }
    conMap["$ip:$port"] = conv
    lastAccessMap[conv] = Date().time
  }
  override fun initServer() {
    udpServer.handler {
      if(it.length<4) return@handler
      val buffer = Buffer.buffer().appendBytes(it.data,it.offset,it.length)
      val conv = conMap["${it.address.hostAddress}:${it.port}"]?:return@handler
      lastAccessMap[conv] = Date().time
      unitMap[conv]?.kcp?.input(buffer)
    }
    udpServer.start()
  }

  override fun send(srcIp: InetAddress, dstIp: InetAddress, srcPort: Short, dstPort: Short, buffer: ByteArray, size: Int) {
    udpServer.send(dstPort.toInt(),dstIp,buffer,size)
  }
}
