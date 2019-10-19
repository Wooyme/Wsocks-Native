package co.zzyun.wsocks.server

import co.zzyun.wsocks.*
import co.zzyun.wsocks.data.UserInfo
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import org.apache.commons.codec.binary.Base64
import java.net.Inet4Address
import java.net.InetAddress
import java.util.*
import kotlin.random.Random

abstract class BaseServer : AbstractVerticle() {
  private val slaveCode by lazy { config().getString("slave") }
  private val centerUrl by lazy { config().getString("center") }
  protected val srcIp: String by lazy { config().getString("host") }
  protected val srcPort: Int by lazy { config().getInteger("udp") }
  private val wndSize by lazy { config().getInteger("WndSize") }
  private val mtu by lazy { config().getInteger("mtu") }
  private val maxWaitSnd by lazy { config().getInteger("maxWaitSnd") }
  private val eventBus by lazy { vertx.eventBus() }
  private val httpPort by lazy { config().getInteger("http") }
  protected val httpServer: HttpServer by lazy { vertx.createHttpServer() }
  protected val router: Router by lazy { Router.router(vertx) }
  private val httpClient by lazy { vertx.createHttpClient() }

  override fun start(startFuture: Future<Void>) {
    router.route("/login").handler {ctx->
      if(ctx.request().getParam("token")==null){
        return@handler ctx.response().setStatusCode(404).end()
      }
      val token = ctx.request().getParam("token")
      httpClient.getAbs("$centerUrl/online?token=$token"){
        if(it.statusCode()==500){
          ctx.response().setStatusCode(500).end()
          return@getAbs
        }
        val conv = Date().time/10000
        val dstPort = it.headers().get("x-port").toShort()
        val dstIp = it.headers().get("x-ip")
        val kcp = newKcp(srcIp,srcPort.toShort(),dstIp,dstPort,conv)
        onLogin(dstIp,dstPort.toInt(),conv)
        deployUnit(Base64.decodeBase64(it.getHeader("x-key")),token,kcp){ id->
          ctx.response().setStatusCode(200)
            .putHeader("x-src-port",this.srcPort.toString())
            .putHeader("x-src-host",this.srcIp)
            .putHeader("x-conv",conv.toString())
            .end()
        }
      }.end()
    }
    initServer()
    httpClient.getAbs("$centerUrl/slave?slave=$slaveCode&port=$httpPort&type=${config().getString("server")}"){}.end()
    httpServer.requestHandler(router).listen(httpPort) { http ->
      if (http.succeeded()) {
        startFuture.complete()
        println("HTTP server started on port $httpPort")
      } else {
        startFuture.fail(http.cause())
      }
    }
  }
  protected abstract fun initServer()
  open fun onLogin(ip:String,port:Int,conv:Long){}
  protected abstract fun send(srcIp: InetAddress,dstIp: InetAddress,srcPort: Short,dstPort:Short,buffer: ByteArray,size:Int)

  private fun newKcp(srcIp:String, srcPort:Short, dstIp:String, dstPort:Short, conv:Long):KCP{
    val srcIpAddress = Inet4Address.getByName(srcIp)
    val dstIpAddress = Inet4Address.getByName(dstIp)
    val kcp =
      object : KCP(conv) {
        override fun output(buffer: ByteArray, size: Int) {
          //UdpUtil.sendUdp(ptr, buffer, size)
          send(srcIpAddress,dstIpAddress,srcPort,dstPort,buffer,size)
        }
      }
    kcp.SetMtu(mtu)
    kcp.WndSize(wndSize, wndSize)
    kcp.NoDelay(1, 20, 2, 1)
    return kcp
  }

  private fun deployUnit(key:ByteArray,token:String, kcp:KCP, cb:(String)->Any){
    val unit = TransportUnit(kcp, key, maxWaitSnd,token,httpClient,centerUrl)
    unitMap[kcp.conv] = unit
    vertx.deployVerticle(unit, DeploymentOptions().setWorker(true).setConfig(config())){ dr->
      val deployId = dr.result()
      cb(deployId)
    }
  }

  protected fun KCP.input(buffer: Buffer){
    eventBus.send("unit-${this.conv}",buffer)
  }
}
