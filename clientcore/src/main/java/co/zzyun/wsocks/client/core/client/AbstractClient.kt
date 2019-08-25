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

abstract class AbstractClient : AbstractVerticle() {
  companion object {
    private const val port = 1090
    val loginCenter = JsonObject().put("action", "login").toBuffer()
    private val handshakeBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x00.toByte())
    private val connSuccessBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x00.toByte()).appendByte(0x00.toByte()).appendByte(0x01.toByte()).appendBytes(ByteArray(6) { 0x0 })
    private val unsupportedBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x08.toByte())
  }

  private val eventBus by lazy { vertx.eventBus() }
  private lateinit var netServer: NetServer
  protected val connectMap = HashMap<String, NetSocket>()
  protected val remotePort by lazy { config().getInteger("remote.port") }
  protected val centerHost by lazy { System.getProperty("center.host") }
  protected val centerPort by lazy { System.getProperty("center.port").toInt() }
  protected val remoteHost: String by lazy { config().getString("remote.host") }
  private val userInfo: UserInfo by lazy { UserInfo.fromJson(config().getJsonObject("user.info")) }

  protected val udpServer by lazy { SimpleUdp(1079) }
  protected var myPort = 0
  protected lateinit var myIp: String
  protected val httpClient: HttpClient by lazy { vertx.createHttpClient(HttpClientOptions().setMaxPoolSize(1).setKeepAlive(true)) }
  lateinit var kcp: KCP

  private fun KCP.input(buffer: Buffer) = eventBus.send("kcp-input", buffer)

  protected fun isKcpInitialized() = this::kcp.isInitialized

  protected var timerID = 0L
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
    preLogin(future)
    initSocksServer()
    udpServer.start()
  }

  override fun stop() {
    println("Stopping client...")
    httpClient.close()
    udpServer.close()
    netServer.close()
    connectMap.clear()
    vertx.cancelTimer(timerID)
    if (this::kcp.isInitialized) kcp.clean()
    super.stop()
  }

  private fun preLogin(msg: Future<Void>) {
    var loginTimerId = 0L
    this.udpServer.handler {
      if (it.port == centerPort) {
        if (myPort != 0) return@handler
        val buffer = Buffer.buffer().appendBytes(it.data,it.offset,it.length)
        //获取本机的公网端口&IP
        vertx.cancelTimer(loginTimerId)
        myPort = try {
          buffer.toJsonObject().getInteger("port")
        } catch (e: Throwable) {
          return@handler msg.fail(e.localizedMessage)
        }
        myIp = try {
          buffer.toJsonObject().getString("host")
        } catch (e: Throwable) {
          return@handler msg.fail(e.localizedMessage)
        }
        httpClient.getNow(remotePort, remoteHost, "/login?&i=$myIp&p=$myPort&s=${userInfo.secret}") {
          if (it.statusCode() != 200) return@getNow msg.fail(it.statusMessage())
          udpServer.send(it.headers()["x-src-port"].toInt(), InetAddress.getByName(it.headers()["x-src-host"]), Buffer.buffer("go"))
          val conv = it.headers()["x-conv"].toLong()
          initKcp(conv)
        }
      } else {
        if (!isKcpInitialized()) return@handler
        kcp.input(Buffer.buffer().appendBytes(it.data,it.offset,it.length))
      }
    }
    loginTimerId = vertx.setPeriodic(1000) {
      this.udpServer.send(centerPort, InetAddress.getByName(centerHost),loginCenter)
    }
  }

  abstract fun initKcp(conv: Long)

  private fun handle(buffer: Buffer) {
    if (buffer.length() < 4) return
    when (buffer.getIntLE(0)) {
      Flag.CONNECT_SUCCESS.ordinal -> connectedHandler(ConnectSuccess(userInfo, buffer).uuid)
      Flag.EXCEPTION.ordinal -> exceptionHandler(Exception(userInfo, buffer))
      Flag.RAW.ordinal -> receivedRawHandler(RawData(userInfo, buffer))
      else -> {
        println("Invalid:${buffer.getIntLE(0)}")
      }
    }
  }

  private fun initSocksServer() {
    println("Init socks server")
    if (this::netServer.isInitialized) {
      this.netServer.close()
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
          kcp.Send(RawData.create(userInfo, uuid, it).bytes)
        } else {
          bufferHandler(uuid, socket, it)
        }
      }.closeHandler {
        connectMap.remove(uuid)?.handler(null)
        kcp.Send(Exception.create(userInfo, uuid, "").bytes)
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
    kcp.Send(ClientConnect.create(userInfo, uuid, host, port).bytes)
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
