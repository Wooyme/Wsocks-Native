package co.zzyun.wsocks.client.core.client

import co.zzyun.wsocks.client.core.KCP
import io.vertx.core.buffer.Buffer

class HttpUdp : AbstractClient() {
  override fun initKcp(conv: Long) {
    kcp = object : KCP(conv) {
      override fun output(buffer: ByteArray, size: Int) {
        val req = httpClient.post(remotePort, remoteHost, "/transport?c=$conv") {
        }
        req.end(Buffer.buffer().appendBytes(buffer, 0, size))
      }
    }
    kcp.SetMtu(1400)
    kcp.WndSize(256, 256)
    kcp.NoDelay(1, 10, 2, 1)
  }
}
