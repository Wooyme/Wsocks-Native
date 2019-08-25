package co.zzyun.wsocks.server

import co.zzyun.wsocks.unitMap

class WebSocketUdp:AbstractServer() {
  override fun initServer() {
    httpServer.websocketHandler {
      val conv = try {
        it.headers()["c"].toLong()
      } catch (e: Throwable) {
        return@websocketHandler it.reject(404)
      }
      val unit = unitMap[conv]?:return@websocketHandler it.reject(404)
      val kcp = unit.kcp
      val id = unit.deploymentID()
      it.binaryMessageHandler { buf-> kcp.input(buf) }
        .exceptionHandler { vertx.undeploy(id) }
        .closeHandler { vertx.undeploy(id) }
    }
  }
}
