package co.zzyun.wsocks.redis

import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.redis.RedisClient
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RedisConnection(private val id: String, private val vertx: Vertx, private val client: RedisClient, val info: JsonObject, private val flagPair: Pair<String, String> = ("c" to "s")) {
  companion object {
    const val DEFAULT = 2
    const val mag = 2
    const val max= 8
    const val delay = 20L
  }
  private lateinit var stopHandler: Handler<Void>
  private lateinit var handler: Handler<Buffer>
  private val offsetRead = AtomicInteger(0)
  private val offsetWrite = AtomicInteger(0)
  private val tempOffsetRead = AtomicInteger(0)
  private var wnd = AtomicInteger(DEFAULT)
  private var stopped = AtomicBoolean(false)
  private val timerId = vertx.setPeriodic(delay) {
    val offset = offsetRead.get()
    val cWnd = wnd.get()
    (0 until cWnd).map { i ->
      val future = Future.future<Pair<Int, Boolean>>()
      val flag = id + flagPair.first + Integer.toHexString(offset + i)
      client.getBinary(flag) {
        if(!stopped.get()) {
          if (it.succeeded()) {
            if (it.result() != null) {
              val data = it.result()
              client.del(flag) {}
              handler.handle(data)
              future.complete(offset + i to true)
            } else {
              future.complete(offset + i to false)
            }
          } else {
            this.stop()
            this.stopHandler.handle(null)
          }
        }
      }
      future
    }.let {
      val beginTime = Date()
      CompositeFuture.all(it).setHandler {
        val lag = Date().time - beginTime.time
        val list = it.result().list<Pair<Int, Boolean>>().filter { it.second }.sortedBy { it.first }
        if (list.isNotEmpty()) {
          if (tempOffsetRead.get() < list.last().first)
            tempOffsetRead.set(list.last().first)
          if(lag > 10* delay) {
            wnd.set(DEFAULT)
          }else if (list.size >= wnd.get()) {
            if (wnd.get() < max)
              if (wnd.get() * mag <= max) {
                wnd.accumulateAndGet(mag) { x, y -> x * y }
              } else {
                wnd.set(max)
              }
            else if (wnd.get() >= cWnd && list.size < wnd.get() / 2) {
              wnd.set(DEFAULT)
            }
          }
        } else {
          if (wnd.get() >=cWnd)
            wnd.set(DEFAULT)
          if (offsetRead.get() > tempOffsetRead.get())
            offsetRead.set(tempOffsetRead.get())
        }
        println("wnd:${wnd.get()},lag:$lag")
      }
    }
    offsetRead.addAndGet(cWnd)
  }
  fun handler(handler: Handler<Buffer>) {
    this.handler = handler
  }

  fun stopHandler(handler:Handler<Void>){
    this.stopHandler = handler
  }

  fun write(data: Buffer) {
    if(!stopped.get()) {
      val offset = offsetWrite.getAndAdd(1)
      client.setBinary(id + flagPair.second + Integer.toHexString(offset), data) {}
    }
  }

  fun stop() {
    if(!stopped.get()){
      stopped.set(true)
      println("[${Date()}] Connection[$id] stopped")
      client.setBinary(id,Buffer.buffer("shutdown")){}
      vertx.cancelTimer(timerId)
    }
  }

  fun start() {
    println("[${Date()}] Connection[$id] started")
    client.setBinary(id, Buffer.buffer("success")){
      if(it.failed()){
        this.stop()
        this.stopHandler.handle(null)
      }
    }
  }
}
