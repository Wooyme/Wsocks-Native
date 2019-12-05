package co.zzyun.wsocks

import co.zzyun.wsocks.server.receiver.TcpRecv
import co.zzyun.wsocks.server.receiver.WebSocketRecv
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.file.FileSystemOptions
import io.vertx.core.json.JsonObject
import java.io.File

val unitMap = HashMap<Long, TransportUnit>()

fun main(args: Array<String>) {
  System.setProperty("io.netty.noUnsafe","true")
  val serverConfig = JsonObject(File(args[0]).readText())
  val serverImpl = when(serverConfig.getString("server")){
    "websocket"->WebSocketRecv()
    "tcp" -> TcpRecv()
    else-> WebSocketRecv()
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
