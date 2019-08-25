package co.zzyun.wsocks.server

import co.zzyun.wsocks.SimpleUdp
import co.zzyun.wsocks.unitMap
import io.vertx.core.buffer.Buffer

class FullUdp:AbstractServer() {
  private val udpServer by lazy { SimpleUdp(srcPort) }
  private val conMap = HashMap<String,Long>()
  override fun onLogin(ip:String,port:Int,conv:Long){
    conMap["$ip:$port"] = conv
  }
  override fun initServer() {
    udpServer.handler {
      if(it.length<4) return@handler
      val buffer = Buffer.buffer().appendBytes(it.data,it.offset,it.length)
      unitMap[conMap["${it.address.hostAddress}:${it.port}"]]?.kcp?.input(buffer)
    }
    udpServer.start()
  }
}
