package co.zzyun.wsocks.client.core.client

import co.zzyun.wsocks.client.core.KCP
import io.vertx.core.Future
import io.vertx.core.MultiMap
import io.vertx.core.buffer.Buffer
import java.net.InetAddress
import java.util.*

class WebsocketUdp : AbstractClient() {
  override fun initKcp(conv: Long) {
    val headers = MultiMap.caseInsensitiveMultiMap().add("c", conv.toString())
    httpClient.websocket(remotePort, remoteHost, "/talk", headers) {
      val ws = it.closeHandler {
        this.stop()
      }.exceptionHandler {
        println(it.localizedMessage)
      }
      kcp = object : KCP(conv) {
        override fun output(buffer: ByteArray, size: Int) {
          ws.writeBinaryMessage(Buffer.buffer().appendBytes(buffer, 0, size))
        }
      }
      kcp.SetMtu(1400)
      kcp.WndSize(256, 256)
      kcp.NoDelay(1, 10, 2, 1)
    }
  }
}
