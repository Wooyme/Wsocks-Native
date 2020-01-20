package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.TransportUnit
import co.zzyun.wsocks.server.api.CenterApi
import co.zzyun.wsocks.server.sender.ISender
import co.zzyun.wsocks.unitMap
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.client.WebClient
import org.apache.commons.codec.binary.Base64
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

abstract class AbstractReceiver<T:Any>:AbstractVerticle() {
  private val id by lazy { config().getString("id") }
  private val host by lazy { config().getString("host") }
  private val centerHost by lazy { config().getJsonObject("center").getString("host") }
  private val centerPort by lazy { config().getJsonObject("center").getInteger("port") }
  private val wndSize by lazy { config().getInteger("WndSize") }
  private val mtu by lazy { config().getInteger("mtu") }
  private val maxWaitSnd by lazy { config().getInteger("maxWaitSnd") }
  private val eventBus by lazy { vertx.eventBus() }
  private val httpClient by lazy { vertx.createHttpClient(HttpClientOptions().setConnectTimeout(20*1000)) }
  private val udpServer by lazy { vertx.createDatagramSocket() }
  protected val senderMap = HashMap<String,ISender>()
  protected val loginPort:Int by lazy { config().getInteger("login") }
  private val centerApi by lazy { CenterApi(centerPort,centerHost,WebClient.create(vertx)) }
  override fun start() {
    super.start()
    udpServer.handler {
      val host = it.sender().host()
      val port = it.sender().port()
      udpServer.send(JsonObject().put("port",port).put("host",host).toBuffer(),port,host){}
    }.listen(loginPort,"0.0.0.0"){
      println("Udp Server Listen at $loginPort")
    }
    this.initServer{ recvType,token,conn,address,conv,cb ->
      centerApi.validate(token).setHandler {
        if(it.failed()){
          println("Client:[$token] login failed")
          cb(null)
          return@setHandler
        }
        println("Client:[$token] login success")
        val sender = senderMap[recvType]?:return@setHandler cb(null)
        val kcp = newKcp(conv,sender,conn,address)
        deployUnit(it.result(),token,kcp,conn,sender,address){
          cb(kcp)
        }
      }
    }
    centerApi.online(id,host,loginPort)
    vertx.setPeriodic(3*60*1000){
      centerApi.online(id,host,loginPort)
    }
  }

  abstract fun initServer(onConnect: (String,String,T?, SocketAddress, Long,(KCP?)->Unit) -> Unit)
  abstract fun close(conn:T?,address: SocketAddress)

  private fun newKcp(conv:Long, sender: ISender,conn:T?,address:SocketAddress): KCP {
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

  private fun deployUnit(key:ByteArray, token:String, kcp:KCP,conn: T?,sender:ISender,address: SocketAddress, cb:(String)->Any){
    val unit = TransportUnit(kcp, key, maxWaitSnd,token,httpClient,centerApi)
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
