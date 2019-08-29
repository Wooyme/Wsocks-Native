package co.zzyun.wsocks

import co.zzyun.wsocks.server.FullUdp
import co.zzyun.wsocks.server.HttpUdp
import co.zzyun.wsocks.server.PcapUdp
import co.zzyun.wsocks.server.WebSocketUdp
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.file.FileSystemOptions
import io.vertx.core.json.JsonObject
import java.io.File

val unitMap = HashMap<Long, TransportUnit>()

fun main(args: Array<String>) {
  System.setProperty("io.netty.noUnsafe","true")
  //System.loadLibrary("Udp")
  PcapUtil.initPcap(System.getProperty("mac.src"),System.getProperty("mac.gateway"))
  val serverConfig = JsonObject(File(args[0]).readText())

  val serverImpl = when(serverConfig.getString("server")){
    "websocket"-> WebSocketUdp()
    "http"-> HttpUdp()
    "udp"->FullUdp()
    "pcap"->PcapUdp()
    else -> WebSocketUdp()
  }
  val vertxOptions = VertxOptions()
  if(serverConfig.getBoolean("lowendbox")){
    vertxOptions.setEventLoopPoolSize(1)
      .setWorkerPoolSize(1)
      .internalBlockingPoolSize = 1
  }
  Vertx.vertx(vertxOptions.setFileSystemOptions(FileSystemOptions()
    .setFileCachingEnabled(false).setClassPathResolvingEnabled(false))).deployVerticle(serverImpl, DeploymentOptions().setConfig(serverConfig))
}
