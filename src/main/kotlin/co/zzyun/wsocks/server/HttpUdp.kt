package co.zzyun.wsocks.server

import co.zzyun.wsocks.unitMap
import io.vertx.ext.web.handler.BodyHandler

class HttpUdp:AbstractServer() {
  override fun initServer() {
    router.route("/transport").handler(BodyHandler.create())
    router.route("/transport").handler {
      val conv = try {
        it.request().getParam("c").toLong()
      } catch (e: NumberFormatException) {
        return@handler it.response().setStatusCode(404).end()
      }
      unitMap[conv]?.kcp?.input(it.body)
      it.response().end()
    }
  }
}
