package co.zzyun.wsocks

import co.zzyun.wsocks.data.*
import co.zzyun.wsocks.server.api.CenterApi
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.net.NetClientOptions
import io.vertx.core.net.NetSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.HashMap

class TransportUnit(private val kcp:KCP, private val key:ByteArray, private val maxWaitSnd:Int, private val token:String, private val httpClient:HttpClient, private val centerApi: CenterApi):AbstractVerticle() {
  companion object {
      private val debug = System.getProperty("ws.debug")?.toBoolean()?:false
      private val heart = Buffer.buffer().appendIntLE(Flag.HEART.ordinal).bytes
  }
  private val netClient by lazy { vertx.createNetClient(NetClientOptions().setConnectTimeout(120*1000)) }
  private val conMap = HashMap<String,NetSocket>()
  private var timerID:Long = 0L
  private var heartTimerID:Long = 0L
  private var updateTimerID:Long = 0L
  private var lastAccessTs = 0L
  private var usage = 0L
  private var onStop:(()->Any)?=null
  override fun start() {
    super.start()
    val data = ByteArray(8 * 1024 * 1024)
    vertx.eventBus().localConsumer<Buffer>("unit-${kcp.conv}"){
      if(debug){
        println("[unit-send-${kcp.conv}]: DataLen ${it.body().length()}")
      }
      kcp.Input(it.body().bytes)
    }
    this.timerID = vertx.setPeriodic(10) {
      kcp.Update(Date().time)
      var len = kcp.Recv(data)
      while (len > 0) {
        if(debug){
          println("[unit-recv-${kcp.conv}]: DataLen $len")
        }
        handle(Buffer.buffer().appendBytes(data, 0, len))
        len = kcp.Recv(data)
      }
    }
    this.heartTimerID = vertx.setPeriodic(4*1000){
      //10s未收到任何数据则关闭这个单元
      if(lastAccessTs!=0L && Date().time-lastAccessTs>1000*10){
        this.stop()
      }
      kcp.Send(heart)
    }

    this.updateTimerID = vertx.setPeriodic(6*60*1000){
      this.centerApi.update(token,usage)
      this.usage = 0
    }
  }

  override fun stop() {
    super.stop()
    //关闭所有连接，关闭计时器，清空kcp缓存
    conMap.forEach { it.value.close() }
    conMap.clear()
    vertx.cancelTimer(timerID)
    vertx.cancelTimer(heartTimerID)
    unitMap.remove(kcp.conv)
    kcp.clean()
    this.centerApi.leave(token)
    this.centerApi.update(token,usage)
    this.onStop?.invoke()
  }

  fun setOnStop(onStop:()->Any){
    this.onStop = onStop
  }

  private fun handle(buffer:Buffer){
    when (buffer.getIntLE(0)) {
      Flag.CONNECT.ordinal -> clientConnectHandler(ClientConnect(key, buffer))
      Flag.RAW.ordinal -> {
        clientRawHandler(RawData(key, buffer))
      }
      Flag.DNS.ordinal -> clientDNSHandler(DnsQuery(key, buffer))
      Flag.EXCEPTION.ordinal -> clientExceptionHandler(Exception(key, buffer))
      Flag.HEART.ordinal->{
        kcp.Send(heart)
      } // 返回一个heart
      else -> println(buffer.getIntLE(0))
    }
    this.lastAccessTs = Date().time
  }


  private fun clientConnectHandler(data: ClientConnect) {
    netClient.connect(data.port, InetAddress.getByName(data.host).hostAddress) {nr->
      if(nr.failed()){
        kcp.Send(Exception.create(key,data.uuid,nr.cause().localizedMessage))
        return@connect
      }
      val net = nr.result()
      net.handler { buffer->
        fun wait() {
          net.pause()
          vertx.setTimer(500) {
            if(kcp.WaitSnd()>maxWaitSnd) wait() else net.resume()
          }
        }
        if(kcp.WaitSnd()>maxWaitSnd) wait()
        usage+=buffer.length()
        kcp.Send(RawData.create(key, data.uuid, buffer))
      }.closeHandler {
        conMap.remove(data.uuid)
        kcp.Send(Exception.create(key,data.uuid,""))
      }.exceptionHandler {
        conMap.remove(data.uuid)
        kcp.Send(Exception.create(key,data.uuid,""))
      }
      conMap[data.uuid] = net
      kcp.Send(ConnectSuccess.create(key, data.uuid))
    }
  }

  private fun clientRawHandler( data: RawData) {
    conMap[data.uuid]?.write(data.data) ?: let {
      kcp.Send(Exception.create(key, data.uuid, "Remote socket has closed"))
    }
  }

  private fun clientExceptionHandler(data: Exception) {
    if (data.message.isEmpty()) conMap.remove(data.uuid)?.close()
  }

  private fun clientDNSHandler(data: DnsQuery) {
    vertx.executeBlocking<String>({
      val address = try {
        InetAddress.getByName(data.host)?.hostAddress ?: "0.0.0.0"
      } catch (e: Throwable) {
        "0.0.0.0"
      }
      it.complete(address)
    }) {
      kcp.Send(DnsQuery.create(key, data.uuid, it.result()))
    }
  }
}
