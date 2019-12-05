package co.zzyun.wsocks.client.core.client

import co.zzyun.wsocks.client.core.KCP
import co.zzyun.wsocks.client.core.SimpleUdp
import co.zzyun.wsocks.data.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetServer
import io.vertx.core.net.NetSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.*

class BaseClient : AbstractVerticle() {
  companion object {
    private const val port = 1090
    private val handshakeBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x00.toByte())
    private val connSuccessBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x00.toByte()).appendByte(0x00.toByte()).appendByte(0x01.toByte()).appendBytes(ByteArray(6) { 0x0 })
    private val unsupportedBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x08.toByte())
  }

  private val eventBus by lazy { vertx.eventBus() }
  private lateinit var netServer: NetServer
  private val connectMap = HashMap<String, NetSocket>()
  private var remotePort: Int = 0
  private val centerHost by lazy { config().getString("center.host") }
  private val centerPort by lazy { config().getInteger("center.port") }
  private lateinit var remoteHost: String
  private var xSrcPort: Int = 0
  private lateinit var xSrcHost: String
  private val userInfo: UserInfo by lazy { UserInfo.fromJson(config().getJsonObject("user.info")) }

  private val udpServer by lazy { vertx.createDatagramSocket() }
  private lateinit var myToken: String
  private val httpClient: HttpClient by lazy { vertx.createHttpClient(HttpClientOptions().setMaxPoolSize(1).setKeepAlive(true)) }
  lateinit var kcp: KCP

  private fun KCP.input(buffer: Buffer) = eventBus.send("kcp-input", buffer)

  private fun isKcpInitialized() = this::kcp.isInitialized
  private val heart = Buffer.buffer().appendIntLE(Flag.HEART.ordinal).bytes
  private var lastAccessTs = 0L
  private var heartTimerID = 0L
  private var timerID = 0L
  private var isOffline = true

  override fun start(future: Future<Void>) {
    val data = ByteArray(8 * 1024 * 1024)
    this.timerID = vertx.setPeriodic(10) {
      if (!this::kcp.isInitialized) return@setPeriodic
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
      this.remoteHost = msg.body().getString("host")
      this.remotePort = msg.body().getInteger("port")
      httpClient.get(remotePort, remoteHost, "/login?token=$myToken") {
        if (it.statusCode() != 200) return@get msg.fail(it.statusCode(),it.statusMessage())
        xSrcHost = it.headers()["x-src-host"]
        xSrcPort = it.headers()["x-src-port"].toInt()
        udpServer.send(Buffer.buffer("go"),it.headers()["x-src-port"].toInt(),it.headers()["x-src-host"]){}
        val conv = it.headers()["x-conv"].toLong()
        initKcp(conv)
        this.isOffline = false
        msg.reply("OK")
      }.exceptionHandler {
        msg.fail(499,it.localizedMessage)
      }.end()
    }
    vertx.eventBus().consumer<Any>("status"){ msg->
      if(this.isOffline) msg.reply("")
      else msg.reply("OK")
    }
    preLogin(future)
    initSocksServer()
    udpServer.listen(1079,"0.0.0.0"){}
  }

  private fun initKcp(conv: Long) {
    val inet = InetAddress.getByName(xSrcHost)
    kcp = object : KCP(conv) {
      override fun output(buffer: ByteArray, size: Int) {
        udpServer.send(Buffer.buffer().appendBytes(buffer, 0, size),xSrcPort, inet.hostAddress){}
      }
    }
    kcp.SetMtu(1200)
    kcp.WndSize(256, 256)
    kcp.NoDelay(1, 10, 2, 1)
    this.heartTimerID = vertx.setPeriodic(60*1000){
      //5分钟未收到任何数据则关闭client
      if(lastAccessTs!=0L && Date().time-lastAccessTs>1000*60*5){
        this.offline()
      }
      kcp.Send(heart)
    }
  }

  override fun stop() {
    println("Stopping client...")
    offline()
    httpClient.close()
    udpServer.close()
    connectMap.clear()
    super.stop()
  }


  private fun preLogin(msg: Future<Void>) {
    var loginTimerId = 0L
    this.udpServer.handler {
      if (it.sender().port() == centerPort) {
        if (this::myToken.isInitialized) return@handler
        val buffer = it.data()
        vertx.cancelTimer(loginTimerId)
        val status = try {
          buffer.toJsonObject().getInteger("status")
        } catch (e: Throwable) {
          return@handler msg.fail(e.localizedMessage)
        }
        if (status == -1) {
          return@handler msg.fail("Wrong username or password")
        }
        if (status == -2) {
          return@handler msg.fail("Full device connection")
        }
        myToken = try {
          buffer.toJsonObject().getString("token")
        } catch (e: Throwable) {
          return@handler msg.fail(e.localizedMessage)
        }
        msg.complete()
      } else {
        if (!isKcpInitialized()) return@handler
        kcp.input(it.data())
      }
    }
    loginTimerId = vertx.setPeriodic(1000) {
      this.udpServer.send(JsonObject().put("user", userInfo.username).put("pass", userInfo.password).toBuffer(),centerPort, InetAddress.getByName(centerHost).hostAddress){}
    }
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
    vertx.cancelTimer(timerID)
    vertx.cancelTimer(heartTimerID)
    if (this::kcp.isInitialized) kcp.clean()
    this.netServer.close()
  }

  private fun initSocksServer() {
    println("Init socks server")
    if (this::netServer.isInitialized) {
      try { this.netServer.close() }catch(ignored:Throwable){}
    }
    println("Create socks server")
    this.netServer = vertx.createNetServer().connectHandler { socket ->
      //收到来自浏览器的连接，但是通道还没有完成，则关闭连接
      if (!this::kcp.isInitialized) {
        socket.close()
        return@connectHandler
      }
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
