package co.zzyun.wsocks.client.core.client

import client.Tray
import co.zzyun.wsocks.client.core.KCP
import co.zzyun.wsocks.data.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.MultiMap
import io.vertx.core.buffer.Buffer
import io.vertx.core.datagram.DatagramSocket
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetServer
import io.vertx.core.net.NetSocket
import java.net.Inet4Address
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class BaseClient(private val userInfo: UserInfo) : AbstractVerticle() {
  companion object {
    private const val port = 1080
    private val handshakeBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x00.toByte())
    private val connSuccessBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x00.toByte()).appendByte(0x00.toByte()).appendByte(0x01.toByte()).appendBytes(ByteArray(6) { 0x0 })
    private val unsupportedBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x08.toByte())
    private val heart = Buffer.buffer().appendIntLE(Flag.HEART.ordinal).bytes
  }
  private lateinit var netServer:NetServer
  private val connectMap = ConcurrentHashMap<String, NetSocket>()
  private lateinit var udpServer:DatagramSocket
  private lateinit var kcp: KCP
  private val isKcpInitialized:Boolean get() = this::kcp.isInitialized
  private var lastAccessTs = 0L
  private var heartTimerID = 0L
  private var timerID = 0L
  private val httpClient by lazy { vertx.createHttpClient(HttpClientOptions().setTcpNoDelay(true)) }
  private var webSocketClient: io.vertx.core.http.WebSocket?=null
  var statusMessage = ""
  private fun log(message:String){
    statusMessage += "[${Date().toLocaleString()}]: $message\n"
  }
  private fun initKcp(conv: Long):KCP {
    kcp = object : KCP(conv) {
      override fun output(buffer: ByteArray, size: Int) {
        try {
            webSocketClient?.writeBinaryMessage(Buffer.buffer().appendBytes(buffer,0,size))
              ?:println("WebSocket is closed")
        }catch(e:Throwable){
          log("远程连接断开")
          offline()
        }
      }
    }
    kcp.SetMtu(1200)
    kcp.WndSize(256, 256)
    kcp.NoDelay(1, 10, 2, 1)

    val data = ByteArray(8 * 1024 * 1024)
    this.timerID = vertx.setPeriodic(10) {
      if (!isKcpInitialized) return@setPeriodic
      kcp.Update(Date().time)
      var len = kcp.Recv(data)
      while (len > 0) {
        handle(Buffer.buffer().appendBytes(data, 0, len))
        len = kcp.Recv(data)
      }
    }
    return kcp
  }

  override fun stop() {
    println("Stopping client...")
    log("关闭本地服务器")
    offline()
    super.stop()
  }


  private fun initUdp(remoteHost:String, remotePort:Int):Future<JsonObject>{
    var reqTimerId = 0L
    var times = 0
    val future = Future.future<JsonObject>()
    this.udpServer.handler {
      if (reqTimerId!=0L) {
        val buffer = it.data()
        val json = try {
          buffer.toJsonObject()
        } catch (e: Throwable) {
          return@handler future.fail(e)
        }
        vertx.cancelTimer(reqTimerId)
        reqTimerId=0L
        future.complete(json)
      } else {
        if (isKcpInitialized) kcp.Input(it.data().bytes)
      }
    }
    log("获取本机公网端口")
    reqTimerId = vertx.setPeriodic(1000) {
      if(times>10){
        vertx.cancelTimer(reqTimerId)
        future.fail("公网端口请求超时")
      }
      this.udpServer.send("GO",remotePort, remoteHost){}
      times++
    }
    return future
  }

  private fun handle(buffer: Buffer) {
    if (buffer.length() < 4) return
    when (buffer.getIntLE(0)) {
      Flag.CONNECT_SUCCESS.ordinal -> connectedHandler(ConnectSuccess(userInfo.key, buffer).uuid)
      Flag.EXCEPTION.ordinal -> exceptionHandler(Exception(userInfo.key, buffer))
      Flag.RAW.ordinal -> receivedRawHandler(RawData(userInfo.key, buffer))
    }
    this.lastAccessTs = Date().time
  }

  private fun offline(){
    println("-----------------Offline-----------------")
    log("清空连接...")
    Tray.setStatus("无连接")
    if(timerID!=0L) vertx.cancelTimer(timerID)
    if(heartTimerID!=0L) vertx.cancelTimer(heartTimerID)
    lastAccessTs = 0L
    if (isKcpInitialized) kcp.clean()
    if(this::netServer.isInitialized) try { netServer.close() }catch (ignored:Throwable){}
    try{
      webSocketClient?.close()
      webSocketClient = null
    }catch (ignored:Throwable){}
    connectMap.clear()
  }

  private fun online(){
    println("-----------------online-----------------")
    log("启动连接")
    this.udpServer = vertx.createDatagramSocket()
    this.netServer = vertx.createNetServer()
  }

  fun reconnect(name:String,token:String,remoteHost:String,remotePort:Int,type:String):Future<Void>{
    val fut = Future.future<Void>()
    this.offline()
    this.online()
    val conv = Date().time/1000
    val json = JsonObject().put("token",token).put("conv",conv)
    if(type=="udp"){
      initUdp(remoteHost,remotePort).setHandler {
        if(it.failed()){
          log("公网端口请求超时")
        }else {
          log("获取本机公网端口成功 IP:${it.result().getString("host")},端口:${it.result().getInteger("port")}")
          json.put("recv", "pcap")
          json.put("host", it.result().getString("host"))
          json.put("port", it.result().getInteger("port"))

          httpClient.websocket(remotePort,remoteHost,"/chat", MultiMap.caseInsensitiveMultiMap().apply {
            this["info"] = Base64.getEncoder().encodeToString(json.toString().toByteArray())
          },{
            this.webSocketClient = it
            it.binaryMessageHandler {
              kcp.Input(it.bytes)
            }
            initSocksServer(initKcp(conv))
            this.heartTimerID = vertx.setPeriodic(2*1000){
              //10s未收到任何数据则关闭client
              if(lastAccessTs!=0L && Date().time-lastAccessTs>1000*10){
                log("连接超时")
                this.reconnect(name,token, remoteHost, remotePort, type).setHandler {
                  if(it.succeeded())
                    Tray.setStatus(name)
                  else{
                    Tray.setStatus("连接失败")
                  }
                }
              }
              kcp.Send(heart)
            }
            fut.complete()
          }){
            log("拒绝连接")
            fut.fail(it)
          }
        }
      }
    }else{
      json.put("recv","websocket")
      httpClient.websocket(remotePort,remoteHost,"/chat", MultiMap.caseInsensitiveMultiMap().apply {
        this["info"] = Base64.getEncoder().encodeToString(json.toString().toByteArray())
      },{
        this.webSocketClient = it
        it.binaryMessageHandler {
          kcp.Input(it.bytes)
        }
        initSocksServer(initKcp(conv))
        this.heartTimerID = vertx.setPeriodic(2*1000){
          //10s未收到任何数据则关闭client
          if(lastAccessTs!=0L && Date().time-lastAccessTs>1000*10){
            log("连接超时")
            this.reconnect(name,token, remoteHost, remotePort, type).setHandler {
              if(it.succeeded())
                Tray.setStatus(name)
              else{
                Tray.setStatus("连接失败")
              }
            }
          }
          kcp.Send(heart)
        }
        fut.complete()
      }){
        fut.fail(it)
        log("拒绝连接")
      }
    }
    return fut
  }


  private fun initSocksServer(kcp:KCP) {
    println("Init socks server")
    try { this.netServer.close() }catch(ignored:Throwable){}
    this.netServer.connectHandler { socket ->
      val uuid = UUID.randomUUID().toString()
      socket.handler {
        //如果连接已经建立，则直接使用通道，否则进入socks5连接过程
        if (connectMap.containsKey(uuid)) {
          kcp.Send(RawData.create(userInfo.key, uuid, it))
        } else {
          bufferHandler(uuid, socket, it)
        }
      }.closeHandler {
        connectMap.remove(uuid)?.handler(null)
        kcp.Send(Exception.create(userInfo.key, uuid, ""))
      }.exceptionHandler {
        try {
          connectMap.remove(uuid)?.close()
        } catch (e: Throwable) {
        }
      }
    }.listen(port) {
      println("Listen at $port")
      log("启动Socks5服务器")
    }
  }

  private fun bufferHandler(uuid: String, netSocket: NetSocket, buffer: Buffer) {
    val version = buffer.getByte(0)
    if (version != 0x05.toByte()) {
      netSocket.close()
    }
    when {
      buffer.getByte(1).toInt() + 2 == buffer.length() -> {
        handshakeHandler(netSocket)
      }
      else -> requestHandler(uuid, netSocket, buffer)
    }
  }

  private fun handshakeHandler(netSocket: NetSocket) {
    netSocket.write(handshakeBuf)
  }

  private fun requestHandler(uuid: String, netSocket: NetSocket, buffer: Buffer) {
    /*
    * |VER|CMD|RSV|ATYP|DST.ADDR|DST.PORT|
    * -----------------------------------------
    * | 1 | 1 |0x0| 1  |Variable|   2    |
    * -----------------------------------------
    * */
    val cmd = buffer.getByte(1)
    val addressType = buffer.getByte(3)
    val (host, port) = when (addressType) {
      0x01.toByte() -> {
        val host = Inet4Address.getByAddress(buffer.getBytes(4, 8)).toString().removePrefix("/")
        val port = buffer.getShort(8).toInt()
        host to port
      }
      0x03.toByte() -> {
        val hostLen = buffer.getByte(4).toInt()
        val host = buffer.getString(5, 5 + hostLen)
        val port = buffer.getShort(5 + hostLen).toInt()
        host to port
      }
      else -> {
        netSocket.write(unsupportedBuf)
        return
      }
    }
    when (cmd) {
      0x01.toByte() -> {
        tryConnect(uuid, netSocket, host, port)
      }
      else -> {
        netSocket.write(Buffer.buffer()
          .appendByte(0x05.toByte())
          .appendByte(0x07.toByte()))
        return
      }
    }
  }


  private fun tryConnect(uuid: String, netSocket: NetSocket, host: String, port: Int) {
    connectMap[uuid] = netSocket
    kcp.Send(ClientConnect.create(userInfo.key, uuid, host, port))
  }

  private fun connectedHandler(uuid: String) {
    val netSocket = connectMap[uuid] ?: return
    netSocket.write(connSuccessBuf)
  }

  private fun receivedRawHandler(data: RawData) {
    val netSocket = connectMap[data.uuid] ?: return
    val message = data.data
    netSocket.write(message)
  }

  private fun exceptionHandler(e: Exception) {
    if (e.message.isNotEmpty())
      println("Exception:${e.message}")
    connectMap.remove(e.uuid)?.close()
  }
}
