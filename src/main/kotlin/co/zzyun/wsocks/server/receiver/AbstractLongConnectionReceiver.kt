package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import io.vertx.core.net.SocketAddress

abstract class AbstractLongConnectionReceiver<T:Any> :AbstractReceiver<T>(){
  override fun getConnection(conn: T): SocketAddress {
    throw NotImplementedError()
  }

  override fun onData(conn: T, kcp: KCP) {
    throw NotImplementedError()
  }
}
