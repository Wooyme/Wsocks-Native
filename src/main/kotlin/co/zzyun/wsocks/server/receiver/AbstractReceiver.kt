package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.TransportUnit
import co.zzyun.wsocks.server.sender.ISender
import co.zzyun.wsocks.server.sender.RawUdpSender
import co.zzyun.wsocks.unitMap
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress
import org.apache.commons.codec.binary.Base64
import java.util.*
import kotlin.collections.HashMap

abstract class AbstractReceiver<T:Any>:AbstractVerticle() {
  private val name by lazy { config().getString("name") }
  private val slaveCode by lazy { config().getString("slave") }
  private val centerUrl by lazy { config().getString("center") }
  private val wndSize by lazy { config().getInteger("WndSize") }
  private val mtu by lazy { config().getInteger("mtu") }
  private val maxWaitSnd by lazy { config().getInteger("maxWaitSnd") }
  private val eventBus by lazy { vertx.eventBus() }
  private val httpClient by lazy { vertx.createHttpClient() }
  protected val senderMap = HashMap<String,ISender>()
  protected val loginPort:Int by lazy { config().getInteger("login") }
  protected abstract val type:String
  override fun start() {
    super.start()
    this.initServer{ recvType,token,conn,address,cb ->
      httpClient.getAbs("$centerUrl/auth?token=$token"){
        if(it.statusCode()==500){
          cb(null)
          return@getAbs
        }
        val conv = Date().time/10000
        val sender = senderMap[recvType]?:return@getAbs cb(null)
        val kcp = newKcp(conv,sender,conn,address)
        deployUnit(Base64.decodeBase64(it.getHeader("x-key")),token,kcp,conn,sender,address){
          cb(kcp)
        }
      }.end()
    }
    httpClient.getAbs("$centerUrl/slave?slave=$slaveCode&name=$name&type=$type&login_port=$loginPort"){}.end()
  }

  abstract fun initServer(onConnect: (String,String,T?, SocketAddress?, (KCP?)->Unit) -> Unit)
  abstract fun close(conn:T?,address: SocketAddress?)

  private fun newKcp(conv:Long, sender: ISender,conn:T?,address:SocketAddress?): KCP {
    val kcp =
      object : KCP(conv,eventBus) {
        override fun output(buffer: ByteArray, size: Int) {
          sender.send(conn,address,buffer,size)
        }
      }
    kcp.SetMtu(mtu)
    kcp.WndSize(wndSize, wndSize)
    kcp.NoDelay(1, 20, 2, 1)
    return kcp
  }

  private fun deployUnit(key:ByteArray, token:String, kcp:KCP,conn: T?,sender:ISender,address: SocketAddress?, cb:(String)->Any){
    val unit = TransportUnit(kcp, key, maxWaitSnd,token,httpClient,centerUrl)
    unit.setOnStop {
      this.close(conn,address)
      sender.close(conn, address)
    }
    unitMap[kcp.conv] = unit
    vertx.deployVerticle(unit, DeploymentOptions().setWorker(true).setConfig(config())){ dr->
      val deployId = dr.result()
      cb(deployId)
    }
  }
}
