package co.zzyun.wsocks

import co.zzyun.wsocks.server.receiver.AMQPReceiver
import co.zzyun.wsocks.server.receiver.MemcachedReceiver
import co.zzyun.wsocks.server.receiver.RedisReceiver
import co.zzyun.wsocks.server.receiver.WebSocketRecv
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.file.FileSystemOptions
import io.vertx.core.json.JsonObject
import java.io.File

val unitMap = HashMap<Long, TransportUnit>()

fun main(args: Array<String>) {
  val serverImpl = when(args[0]){
    "memcached"->MemcachedReceiver()
    "redis"->RedisReceiver()
    "amqp"->AMQPReceiver()
    else->WebSocketRecv()
  }
  val serverConfig = JsonObject(File(args[1]).readText())
  val vertxOptions = VertxOptions()
  if(serverConfig.getBoolean("lowendbox")){
    vertxOptions.setEventLoopPoolSize(2)
      .setWorkerPoolSize(2)
      .internalBlockingPoolSize = 1
  }
  val vertx = Vertx.vertx(vertxOptions.setFileSystemOptions(FileSystemOptions()
    .setFileCachingEnabled(false).setClassPathResolvingEnabled(false)))
  vertx.deployVerticle(serverImpl, DeploymentOptions().setConfig(serverConfig))
}
