package co.zzyun.wsocks.client.core.client

import co.zzyun.wsocks.client.core.KCP
import io.vertx.core.buffer.Buffer
import java.net.InetAddress

class FullUdp : AbstractClient() {
  override fun initKcp(conv: Long) {
    val inet = InetAddress.getByName(remoteHost)
    kcp = object : KCP(conv) {
      override fun output(buffer: ByteArray, size: Int) {
        udpServer.send(remotePort, inet, Buffer.buffer().appendBytes(buffer, 0, size))
      }
    }
    kcp.SetMtu(1400)
    kcp.WndSize(256, 256)
    kcp.NoDelay(1, 10, 2, 1)
  }
}
