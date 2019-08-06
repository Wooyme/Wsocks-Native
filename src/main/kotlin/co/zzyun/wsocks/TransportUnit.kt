package co.zzyun.wsocks

import co.zzyun.wsocks.data.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.net.NetSocket
import java.net.InetAddress
import java.util.*
import kotlin.collections.HashMap

class TransportUnit(private val req:HttpServerRequest,private val kcp:KCP,private val userInfo:UserInfo,private val maxWaitSnd:Int):AbstractVerticle() {
  private val netClient by lazy { vertx.createNetClient() }
  private val data = ByteArray(8 * 1024 * 1024)
  private val conMap = HashMap<String,NetSocket>()
  private var timerID:Long = 0L
  override fun start() {
    super.start()
    req.netSocket().closeHandler {
      this.stop()
    }
    this.timerID = vertx.setPeriodic(10) {
      kcp.Update(Date().time)
      var len = kcp.Recv(data)
      vertx.executeBlocking<Any>({
        while (len > 0) {
          handle(Buffer.buffer().appendBytes(data,0,len))
          it.complete()
          len = kcp.Recv(data)
        }
      }){}
    }
  }

  override fun stop() {
    super.stop()
    conMap.forEach { it.value.close() }
    conMap.clear()
    kcp.clean()

  }

  private fun handle(buffer:Buffer){
    when (buffer.getIntLE(0)) {
      Flag.CONNECT.ordinal -> clientConnectHandler(ClientConnect(userInfo, buffer))
      Flag.RAW.ordinal -> clientRawHandler(RawData(userInfo, buffer))
      Flag.DNS.ordinal -> clientDNSHandler(DnsQuery(userInfo, buffer))
      Flag.EXCEPTION.ordinal -> clientExceptionHandler(co.zzyun.wsocks.data.Exception(userInfo, buffer))
      else -> println(buffer.getIntLE(0))
    }
  }

  private fun clientConnectHandler(data: ClientConnect) {
    netClient.connect(data.port, InetAddress.getByName(data.host).hostAddress) {nr->
      if(nr.failed()){
        kcp.Send(co.zzyun.wsocks.data.Exception.create(userInfo,data.uuid,nr.cause().localizedMessage).bytes)
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
        if(kcp.WaitSnd()>maxWaitSnd) wait() else kcp.Send(RawData.create(userInfo, data.uuid, buffer).bytes)
      }.closeHandler {
        kcp.Send(co.zzyun.wsocks.data.Exception.create(userInfo,data.uuid,nr.cause().localizedMessage).bytes)
        conMap.remove(data.uuid)
      }.exceptionHandler {
        conMap.remove(data.uuid)
      }
      conMap[data.uuid] = net
      kcp.Send(ConnectSuccess.create(userInfo, data.uuid).bytes)
    }
  }

  private fun clientRawHandler( data: RawData) {
    conMap[data.uuid]?.write(data.data) ?: let {
      kcp.Send(co.zzyun.wsocks.data.Exception.create(userInfo, data.uuid, "Remote socket has closed").bytes)
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
      kcp.Send(DnsQuery.create(userInfo, data.uuid, it.result()).bytes)
    }
  }
}
