package co.zzyun.wsocks

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import java.nio.charset.Charset
import java.util.*

fun main() {
  val vertx = Vertx.vertx()
  val eventBus = vertx.eventBus()
  val udpClient = vertx.createDatagramSocket()

  println(0x114514)
  val kcp = object : KCP(0x114514, eventBus) {
    override fun output(buffer: ByteArray, size: Int) {
      udpClient.send(Buffer.buffer().appendBytes(buffer,0,size), 2325, "111.91.162.217") {}
    }
  }
  kcp.WndSize(128, 128)
  kcp.NoDelay(1, 20, 0, 0)
  udpClient.handler {
    kcp.Input(it.data().bytes)
  }
  vertx.setTimer(100){
    println(1)
    kcp.Send(Buffer.buffer("Hello").bytes)
  }
  val buf = ByteArray(2000){0}
  vertx.setPeriodic(50){
    udpClient.send(Buffer.buffer().appendByte(0),2325, "111.91.162.217"){}
    kcp.Update(Date().time)
    val size = kcp.Recv(buf)
    if(size>0) {
      println(Buffer.buffer().appendBytes(buf, 0, size).toString(Charset.defaultCharset()))
    }
  }
}
