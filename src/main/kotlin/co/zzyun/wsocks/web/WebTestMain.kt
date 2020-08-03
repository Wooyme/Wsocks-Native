package co.zzyun.wsocks.web

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import java.io.File
import java.nio.charset.Charset

fun main(){
  val config = JsonObject(File("web_config.json").readText(Charset.defaultCharset()))
  Vertx.vertx().deployVerticle(WebTest(), DeploymentOptions().setConfig(config))
}