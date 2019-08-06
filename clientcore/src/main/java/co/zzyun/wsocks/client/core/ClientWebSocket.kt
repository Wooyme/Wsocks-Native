package co.zzyun.wsocks.client.core

import co.zzyun.wsocks.data.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.WebSocket
import io.vertx.core.http.WebSocketConnectOptions
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetServer
import io.vertx.core.net.NetServerOptions
import io.vertx.core.net.NetSocket
import org.apache.commons.lang3.RandomStringUtils
import java.net.Inet4Address
import java.util.*

class ClientWebSocket : AbstractVerticle() {

  private lateinit var netServer: NetServer
  private val connectMap = HashMap<String, NetSocket>()
  private val port = 1080
  private var remotePort = 1888
  private lateinit var remoteIp: String
  private lateinit var userInfo: UserInfo
  private val httpClient: HttpClient by lazy { vertx.createHttpClient(HttpClientOptions().setMaxPoolSize(1)) }
  private lateinit var ws: WebSocket
  private val pingBuf = Buffer.buffer()
  private val handshakeBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x00.toByte())
  private val connSuccessBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x00.toByte()).appendByte(0x00.toByte()).appendByte(0x01.toByte()).appendBytes(ByteArray(6) { 0x0 })
  private val unsupportedBuf = Buffer.buffer().appendByte(0x05.toByte()).appendByte(0x08.toByte())

  override fun start(promise: Promise<Void>) {
    vertx.eventBus().consumer<JsonObject>("config-modify") {
      if(it.body().size()!=0){
        val property = Property.fromJson(it.body())
        remotePort = property.port
        remoteIp = property.host
        userInfo = UserInfo.fromJson(it.body())
        println(it.body().toString())
      }
      login()
      initSocksServer(port)
    }
    vertx.setPeriodic(5000) {
      if (this::ws.isInitialized)
        ws.writePing(pingBuf)
      println("Connect Map size: ${connectMap.size}")
    }
    promise.complete()
  }

  override fun stop() {
    netServer.close()
    if(this::ws.isInitialized)
      ws.close()
    connectMap.clear()
    super.stop()
  }

  private fun login() {
    val options = WebSocketConnectOptions()
      .setHost(remoteIp)
      .setPort(remotePort)
      .setURI("/"+RandomStringUtils.randomAlphanumeric(5))
      .addHeader(RandomStringUtils.randomAlphanumeric(Random().nextInt(10)+1),userInfo.secret)
    httpClient.webSocket(options) { r ->
      if(r.failed()){
        r.cause().printStackTrace()
        return@webSocket
      }
      val webSocket = r.result()
      webSocket.writePing(pingBuf)
      webSocket.binaryMessageHandler { buffer ->
        if (buffer.length() < 4) return@binaryMessageHandler
        when (buffer.getIntLE(0)) {
          Flag.CONNECT_SUCCESS.ordinal -> wsConnectedHandler(ConnectSuccess(userInfo,buffer).uuid)
          Flag.EXCEPTION.ordinal -> wsExceptionHandler(Exception(userInfo,buffer))
          Flag.RAW.ordinal -> {
            wsReceivedRawHandler(RawData(userInfo,buffer))
          }
          else ->{
            println("Invalid:${buffer.getIntLE(0)}")
          }
        }
        buffer.forceRelease()
      }.exceptionHandler { t ->
        ws.close()
        t.printStackTrace()
        login()
      }
      try {
        this.ws.close()
      }catch (e:Throwable){}
      this.ws = webSocket
      println("Connected to remote server")
      vertx.eventBus().publish("status-modify", JsonObject().put("status", "$remoteIp:$remotePort"))
    }
  }

  private fun initSocksServer(port: Int) {
    println("Init socks server")
    if (this::netServer.isInitialized) {
      this.netServer.close()
    }
    println("Create socks server")
    this.netServer = vertx.createNetServer().connectHandler { socket ->
      if (!this::ws.isInitialized) {
        socket.close()
        return@connectHandler
      }
      val uuid = UUID.randomUUID().toString()
      socket.handler {
        if(connectMap.containsKey(uuid)){
          ws.writeBinaryMessageWithOffset(RawData.create(userInfo,uuid, it))
        }else {
          bufferHandler(uuid, socket, it)
        }
        it.forceRelease()
      }.closeHandler {
        connectMap.remove(uuid)?.handler(null)
        ws.writeBinaryMessageWithOffset(Exception.create(userInfo,uuid,""))
      }.exceptionHandler {
        try{ connectMap.remove(uuid)?.close() }catch(e:Throwable){}
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

  private fun WebSocket.writeBinaryMessageWithOffset(data: Buffer) {
      this.writeBinaryMessage(data){
        data.forceRelease()
      }
  }

  private fun tryConnect(uuid: String, netSocket: NetSocket, host: String, port: Int) {
    connectMap[uuid] = netSocket
    ws.writeBinaryMessageWithOffset(ClientConnect.create(userInfo,uuid, host, port))
  }

  private fun wsConnectedHandler(uuid: String) {
    val netSocket = connectMap[uuid] ?: return
    netSocket.write(connSuccessBuf)
  }

  private fun wsReceivedRawHandler(data: RawData) {
    val netSocket = connectMap[data.uuid] ?: return
    val message = data.data
    netSocket.write(message){
      message.forceRelease()
    }
  }

  private fun wsExceptionHandler(e: Exception) {
    if(e.message.isNotEmpty())
      println("Exception:${e.message}")
    connectMap.remove(e.uuid)?.close()
  }
}

