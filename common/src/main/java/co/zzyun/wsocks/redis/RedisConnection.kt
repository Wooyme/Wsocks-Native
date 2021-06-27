package co.zzyun.wsocks.redis

import co.zzyun.wsocks.Settings
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
  private var stopHandler: Handler<Void> = Handler {  }
  private var handler: Handler<Buffer> = Handler {  }
  private val offsetRead = AtomicInteger(0)
  private val offsetWrite = AtomicInteger(0)
  private val tempOffsetRead = AtomicInteger(0)
  private var wnd = AtomicInteger(Settings.CONNECTION_DEFAULT)
  private var stopped = AtomicBoolean(false)
  private val timerId = vertx.setPeriodic(Settings.CONNECTION_DELAY) {
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
          if(lag > Settings.CONNECTION_LAG_LIMIT) {
            wnd.set(Settings.CONNECTION_DEFAULT)
          }else if (list.size >= wnd.get()) {
            if (wnd.get() < Settings.CONNECTION_MAX)
              if (wnd.get() * Settings.CONNECTION_MAG <= Settings.CONNECTION_MAX) {
                wnd.accumulateAndGet(Settings.CONNECTION_MAG) { x, y -> x * y }
              } else {
                wnd.set(Settings.CONNECTION_MAX)
              }
            else if (wnd.get() >= cWnd && list.size < wnd.get() / 2) {
              wnd.set(Settings.CONNECTION_DEFAULT)
            }
          }
        } else {
          if (wnd.get() >=cWnd)
            wnd.set(Settings.CONNECTION_DEFAULT)
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
      client.setBinary(id,Buffer.buffer("shutdown")){
        client.close{}
      }
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
