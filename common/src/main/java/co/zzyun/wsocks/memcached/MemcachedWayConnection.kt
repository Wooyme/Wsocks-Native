package co.zzyun.wsocks.memcached

import co.zzyun.memcached.MemcachedClient
import co.zzyun.wsocks.Settings
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class MemcachedWayConnection(private val id: String, private val vertx: Vertx, private val client: MemcachedClient, val info: JsonObject, private val flagPair: Pair<String, String> = ("c" to "s")) {
  private lateinit var stopHandler: Handler<Void>
  private lateinit var handler: Handler<Buffer>
  private val offsetRead = AtomicInteger(0)
  private val offsetWrite = AtomicInteger(0)
  private val tempOffsetRead = AtomicInteger(0)
  private val wnd = AtomicInteger(Settings.CONNECTION_DEFAULT)
  private var stopped = false
  private val timerId = vertx.setPeriodic(Settings.CONNECTION_DELAY) {
    val offset = offsetRead.get()
    val cWnd = wnd.get()
    (0 until cWnd).map { i ->
      val future = Future.future<Pair<Int, DataNode?>>()
      val flag = id + flagPair.first + Integer.toHexString(offset + i)
      client.get(flag, Handler {
        if (!stopped) {
          if(it.failed()){
            this.stop()
            this.stopHandler.handle(null)
            return@Handler
          }
            val node = it.result()?.let { DataNode(it) }
            future.tryComplete(offset + i to node)
            if (node != null) {
              client.delete(flag)
              handler.handle(node.buffer)
            }
        }
      });
      future
    }.let {
      val beginTime = Date()
      CompositeFuture.all(it).setHandler {
        val lag = Date().time - beginTime.time
        val list = it.result().list<Pair<Int, DataNode?>>().filter { it.second != null }.sortedBy { it.first }
        if (list.isNotEmpty()) {
          if (tempOffsetRead.get() < list.last().first)
            tempOffsetRead.set(list.last().first)
          if(lag > Settings.CONNECTION_LAG_LIMIT){
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
          if (wnd.get() >= cWnd)
            wnd.set(Settings.CONNECTION_DEFAULT)
          if (offsetRead.get() > tempOffsetRead.get())
            offsetRead.set(tempOffsetRead.get())
        }

        println("wnd:${wnd.get()},lag:$lag")
      }
    }
    offsetRead.addAndGet(cWnd)
  }

  fun write(data: Buffer) {
    val offset = offsetWrite.getAndAdd(1)
    client.set(id + flagPair.second + Integer.toHexString(offset), data, Handler {  })
  }

  fun handler(handler: Handler<Buffer>) {
    this.handler = handler
  }

  fun stopHandler(handler: Handler<Void>) {
    this.stopHandler = handler
  }

  fun stop() {
    println("[${Date()}] Connection[$id] stopped")
    this.stopped = true
    client.set(id, DataNode.shutdown.buffer, Handler {  })
    vertx.cancelTimer(timerId)
  }

  fun start() {
    println("[${Date()}] Connection[$id] started")
    client.set(id, DataNode.success.buffer, Handler {  })
  }
}
