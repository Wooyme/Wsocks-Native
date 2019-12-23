package co.zzyun.wsocks.client.core.client

import co.zzyun.wsocks.client.core.KCP
import co.zzyun.wsocks.data.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.MultiMap
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.WebSocket
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetServer
import io.vertx.core.net.NetSocket
import java.net.Inet4Address
import java.util.*

class BaseClient : AbstractVerticle() {
  companion object {
    private const val port = 1090
    private val handshakeBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x00.toByte())
    private val connSuccessBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x00.toByte()).appendByte(0x00.toByte()).appendByte(0x01.toByte()).appendBytes(ByteArray(6) { 0x0 })
    private val unsupportedBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x08.toByte())
  }

  private val eventBus by lazy { vertx.eventBus() }
  private val netServer: NetServer by lazy { vertx.createNetServer() }
  private val connectMap = HashMap<String, NetSocket>()
  private val centerHost by lazy { config().getString("center.host") }
  private val centerPort by lazy { config().getInteger("center.port") }
  private val userInfo: UserInfo by lazy { UserInfo.fromJson(config().getJsonObject("user.info")) }

  private val udpServer by lazy { vertx.createDatagramSocket() }
  private lateinit var myToken: String
  private val httpClient: HttpClient by lazy { vertx.createHttpClient(HttpClientOptions().setMaxPoolSize(1).setKeepAlive(true)) }
  lateinit var kcp: KCP

  private fun KCP.input(buffer: Buffer) = eventBus.send("kcp-input", buffer)

  private val isKcpInitialized:Boolean get() = this::kcp.isInitialized
  private val heart = Buffer.buffer().appendIntLE(Flag.HEART.ordinal).bytes
  private var lastAccessTs = 0L
  private var heartTimerID = 0L
  private var timerID = 0L
  private var noUdp = false
  private var statusMessage = ""
  private val hosts = JsonArray()
  private fun log(message:String){
    statusMessage += "[${Date().toLocaleString()}]: $message\n"
  }
  override fun start(future: Future<Void>) {
    vertx.eventBus().consumer<Any>("status"){ msg->
      msg.reply(statusMessage)
    }
    vertx.eventBus().consumer<Any>("hosts"){
      it.reply(hosts)
    }
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
    vertx.eventBus().consumer<Buffer>("kcp-input") {
      kcp.Input(it.body().bytes)
    }
    vertx.eventBus().consumer<JsonObject>("client-connect"){ msg->
      val remoteHost = msg.body().getString("host")
      val remotePort = msg.body().getInteger("port")
      val type = msg.body().getString("type")
      reconnect(remoteHost,remotePort,type,!noUdp)
    }
    httpClient.get(this.centerPort,this.centerHost,"/login?user=${userInfo.username}&pass=${userInfo.password}"){
      if(it.statusCode()==200){
        this.myToken = it.getHeader("x-token")
        log("用户${userInfo.username}登录成功")
        it.headers().filter { it.key.startsWith("x-host") }.forEach {
          it.value.split("+").let {
            hosts.add(JsonObject().put("name",it[0]).put("host",it[1]).put("port",it[2].toInt()))
          }
        }
        future.complete()
      }else{
        log("用户${userInfo.username}登录失败，原因${it.statusMessage()}")
        future.fail("用户${userInfo.username}登录失败，原因${it.statusMessage()}")
      }
    }.end()

    udpServer.listen(1079,"0.0.0.0"){
      if(it.failed()){
        this.noUdp = true
        log("UDP监听失败")
      }
    }
  }

  private fun initKcp(conv: Long,ws:WebSocket):KCP {
    kcp = object : KCP(conv) {
      override fun output(buffer: ByteArray, size: Int) {
        try {
          ws.writeBinaryMessage(Buffer.buffer().appendBytes(buffer, 0, size))
        }catch(e:Throwable){
          log("远程连接断开")
          offline()
        }
      }
    }
    kcp.SetMtu(1200)
    kcp.WndSize(256, 256)
    kcp.NoDelay(1, 10, 2, 1)
    this.heartTimerID = vertx.setPeriodic(1000){
      //5分钟未收到任何数据则关闭client
      if(lastAccessTs!=0L && Date().time-lastAccessTs>1000*60*5){
        log("连接超时")
        this.offline()
      }
      kcp.Send(heart)
    }
    return kcp
  }

  override fun stop() {
    println("Stopping client...")
    log("关闭本地服务器")
    offline()
    httpClient.close()
    udpServer.close()
    connectMap.clear()
    super.stop()
  }


  private fun initUdp(centerHost:String,centerPort:Int):Future<JsonObject>{
    var reqTimerId = 0L
    var times = 0
    val future = Future.future<JsonObject>()
    this.udpServer.handler {
      if (it.sender().port() == centerPort) {
        val buffer = it.data()
        vertx.cancelTimer(reqTimerId)
        val json = try {
          buffer.toJsonObject()
        } catch (e: Throwable) {
          return@handler future.fail(e)
        }
        future.complete(json)
      } else {
        if (isKcpInitialized) kcp.input(it.data())
      }
    }
    log("获取本机公网端口")
    reqTimerId = vertx.setPeriodic(1000) {
      if(times>10){
        vertx.cancelTimer(reqTimerId)
        future.fail("公网端口请求超时")
      }
      this.udpServer.send("GO",centerPort, centerHost){}
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
      Flag.HEART.ordinal->{
        this.lastAccessTs = Date().time
      }
      else -> {
        println("Invalid:${buffer.getIntLE(0)}")
      }
    }
  }

  private fun offline(){
    println("-----------------Offline-----------------")
    log("清空连接...")
    if(timerID!=0L) vertx.cancelTimer(timerID)
    if(heartTimerID!=0L)vertx.cancelTimer(heartTimerID)
    if (isKcpInitialized) kcp.clean()
    this.netServer.close()
  }

  private fun reconnect(remoteHost:String,remotePort:Int,type:String,enableUdp:Boolean){
    this.offline()
    val conv = Date().time/1000
    val json = JsonObject().put("token",myToken).put("conv",conv)
    val headers = MultiMap.caseInsensitiveMultiMap()
    if(type=="udp" && enableUdp){
      initUdp(this.centerHost,this.centerPort).setHandler {
        if(it.failed()){
          log("公网端口请求超时")
        }else {
          log("获取本机公网端口成功 IP:${it.result().getString("host")},端口:${it.result().getInteger("port")}")
          json.put("recv", "pcap")
          json.put("host", it.result().getString("host"))
          json.put("port", it.result().getInteger("port"))
          headers["info"] = RSAUtil.encrypt(json.toString(), RSAUtil.publicKey)
          httpClient.websocket(remotePort, remoteHost, "/chat", headers,{
            log("连接到目标服务器 $remoteHost:$remotePort")
            initSocksServer(initKcp(conv, it))
          }){
            log("拒接连接")
          }
        }
      }
    }else{
      json.put("recv","websocket")
      headers["info"] = RSAUtil.encrypt(json.toString(),RSAUtil.publicKey)
      httpClient.websocket(remotePort,remoteHost,"/chat", headers){
        log("连接到目标服务器 $remoteHost:$remotePort")
        it.binaryMessageHandler {
          kcp.input(it)
        }
        initSocksServer(initKcp(conv,it))
      }
    }
  }


  private fun initSocksServer(kcp:KCP) {
    println("Init socks server")
    try { this.netServer.close() }catch(ignored:Throwable){}
    this.netServer.connectHandler { socket ->
      val uuid = UUID.randomUUID().toString()
      socket.handler {
        //如果连接已经建立，则直接使用通道，否则进入socks5连接过程
        if (connectMap.containsKey(uuid)) {
          kcp.Send(RawData.create(userInfo.key, uuid, it).bytes)
        } else {
          bufferHandler(uuid, socket, it)
        }
      }.closeHandler {
        connectMap.remove(uuid)?.handler(null)
        kcp.Send(Exception.create(userInfo.key, uuid, "").bytes)
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
    kcp.Send(ClientConnect.create(userInfo.key, uuid, host, port).bytes)
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
