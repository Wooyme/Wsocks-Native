package co.zzyun.wsocks.server

import co.zzyun.wsocks.*
import co.zzyun.wsocks.data.UserInfo
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import java.net.Inet4Address
import java.util.*
import kotlin.random.Random

abstract class AbstractServer : AbstractVerticle() {
  private val userMap = HashMap<String, UserInfo>()
  protected val srcIp by lazy { config().getString("host") }
  protected val srcPort by lazy { config().getInteger("udp") }
  private val wndSize by lazy { config().getInteger("WndSize") }
  private val mtu by lazy { config().getInteger("mtu") }
  private val maxWaitSnd by lazy { config().getInteger("maxWaitSnd") }
  private val eventBus by lazy { vertx.eventBus() }
  protected val httpServer by lazy { vertx.createHttpServer(HttpServerOptions()) }
  protected val router by lazy { Router.router(vertx) }
  private fun initParams() {
    config().getJsonArray("users").forEach { v ->
      val userInfo = UserInfo.fromJson(v as JsonObject)
      userMap[userInfo.secret] = userInfo
    }
  }

  override fun start(startFuture: Future<Void>) {
    initParams()
    router.route("/login").handler {
      if(it.request().getParam("i")==null || it.request().getParam("s") == null || it.request().getParam("p")==null){
        return@handler it.response().setStatusCode(404).end()
      }
      val conv = Date().time/10000
      val user = this.userMap[it.request().getParam("s")] ?: return@handler it.response().setStatusCode(404).end()
      val dstPort = it.request().getParam("p").toInt()
      val dstIp = it.request().getParam("i")
      val kcp = newKcp(srcIp,srcPort.toShort(),dstIp,dstPort.toShort(),conv)
      onLogin(dstIp,dstPort,conv)
      deployUnit(user,kcp){id->
        it.response().setStatusCode(200)
          .putHeader("x-src-port",this.srcPort.toString())
          .putHeader("x-src-host",this.srcIp)
          .putHeader("x-conv",conv.toString())
          .end()
      }
    }
    initServer()
    httpServer.requestHandler(router).listen(config().getInteger("http")) { http ->
      if (http.succeeded()) {
        startFuture.complete()
        println("HTTP server started on port ${config().getInteger("http")}")
      } else {
        startFuture.fail(http.cause())
      }
    }
  }
  protected abstract fun initServer()
  open fun onLogin(ip:String,port:Int,conv:Long){}

  private fun newKcp(srcIp:String, srcPort:Short, dstIp:String, dstPort:Short, conv:Long):KCP{
    val srcIpAddress = Inet4Address.getByName(srcIp)
    val dstIpAddress = Inet4Address.getByName(dstIp)
    //val ptr = UdpUtil.initRaw(srcIp, srcPort, dstIp, dstPort)
    val kcp =
      object : KCP(conv) {
        override fun output(buffer: ByteArray, size: Int) {
          //UdpUtil.sendUdp(ptr, buffer, size)
          PcapUtil.sendUdp(srcIpAddress,dstIpAddress,srcPort,dstPort,Arrays.copyOfRange(buffer,0,size))
        }
      }
    kcp.SetMtu(mtu)
    kcp.WndSize(wndSize, wndSize)
    kcp.NoDelay(1, 20, 2, 1)
    return kcp
  }

  private fun deployUnit(user: UserInfo, kcp:KCP, cb:(String)->Any){
    val unit = TransportUnit(kcp, user, maxWaitSnd)
    unitMap[kcp.conv] = unit
    vertx.deployVerticle(unit, DeploymentOptions().setWorker(true).setConfig(config())){ dr->
      val deployId = dr.result()
      println("User[${user.username}],Conv[${kcp.conv}] logged in")
      cb(deployId)
    }
  }

  protected fun KCP.input(buffer: Buffer){
    eventBus.send("unit-${this.conv}",buffer)
  }
}
