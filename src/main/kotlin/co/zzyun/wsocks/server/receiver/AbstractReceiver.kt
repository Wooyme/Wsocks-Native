package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.TransportUnit
import co.zzyun.wsocks.server.api.CenterApi
import co.zzyun.wsocks.server.sender.ISender
import co.zzyun.wsocks.unitMap
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Handler
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.client.WebClient
import org.apache.commons.codec.binary.Base64
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

abstract class AbstractReceiver<T:Any>(val isShort: Boolean = false):AbstractVerticle() {
  data class LoginInfo(val socketAddress:SocketAddress,val recvType:String,val token:String,val conv:Long)
  protected val id by lazy { config().getString("id") }
  protected val host by lazy { config().getString("host") }
  private val centerHost by lazy { config().getJsonObject("center").getString("host") }
  private val centerPort by lazy { config().getJsonObject("center").getInteger("port") }
  private val fast by lazy { config().getBoolean("fast")?:true }
  private val wndSize by lazy { config().getInteger("WndSize") }
  private val mtu by lazy { config().getInteger("mtu") }
  private val maxWaitSnd by lazy { config().getInteger("maxWaitSnd") }
  private val eventBus by lazy { vertx.eventBus() }
  private val httpClient by lazy { vertx.createHttpClient(HttpClientOptions().setConnectTimeout(20*1000)) }
  protected val senderMap = HashMap<String,ISender>()
  protected val loginPort:Int by lazy { config().getInteger("login") }
  private val centerApi by lazy { CenterApi(centerPort,centerHost,WebClient.create(vertx)) }
  private val conMap:MutableMap<String,KCP> = ConcurrentHashMap()
  override fun start() {
    super.start()
    this.initServer(Handler {conn->
      if(isShort){
        val address = getConnection(conn)
        conMap[address.host()+address.port()]?.let {
          onData(conn,it)
          return@Handler
        }
      }
      val loginInfo = handleLogin(conn)?:return@Handler onFailed(conn)
      centerApi.validate(loginInfo.token).setHandler {
        if(it.failed()){
          println("Client:[${loginInfo.token}] login failed")
          onFailed(conn)
        }else{
          println("Client:[${loginInfo.token}] login success")
          val sender = senderMap[loginInfo.recvType]?:return@setHandler onFailed(conn)
          val kcp = newKcp(loginInfo.conv,sender,conn,loginInfo.socketAddress)
          deployUnit(it.result().first,loginInfo.token,kcp,conn,sender,loginInfo.socketAddress){
            onConnected(conn,kcp)
            if(isShort){
              conMap[loginInfo.socketAddress.host()+loginInfo.socketAddress.port()] = kcp
            }
          }
        }
      }
    })
    centerApi.online(id,host,loginPort)
    vertx.setPeriodic(3*60*1000){
      centerApi.online(id,host,loginPort)
    }
  }

  abstract fun initServer(handler:Handler<T>)
  abstract fun handleLogin(conn:T):LoginInfo?
  abstract fun getConnection(conn:T):SocketAddress
  abstract fun onData(conn: T,kcp:KCP)
  abstract fun onConnected(conn:T,kcp:KCP)
  abstract fun onFailed(conn:T)
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
    if(fast)
      kcp.NoDelay(1, 20, 2, 1)
    else
      kcp.NoDelay(0,20,0,0)
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
