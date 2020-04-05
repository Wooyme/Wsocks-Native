package co.zzyun.wsocks.memcached

import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import net.spy.memcached.MemcachedClient
import net.spy.memcached.internal.CheckedOperationTimeoutException
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

class MemcachedConnection(private val id: String, private val vertx: Vertx, private val client: MemcachedClient, val info: JsonObject, private val flagPair: Pair<String, String> = ("c" to "s")) {
  companion object {
    const val DEFAULT = 2
    const val mag = 2
    const val max = 8
    const val delay = 10L
  }

  private lateinit var stopHandler: Handler<Void>
  private lateinit var handler: Handler<Buffer>
  private val offsetRead = AtomicInteger(0)
  private val offsetWrite = AtomicInteger(0)
  private val tempOffsetRead = AtomicInteger(0)
  private val wnd = AtomicInteger(DEFAULT)
  private var stopped = false
  private val timerId = vertx.setPeriodic(delay) {
    val offset = offsetRead.get()
    val cWnd = wnd.get()
    (0 until cWnd).map { i ->
      val future = Future.future<Pair<Int, DataNode?>>()
      val flag = id + flagPair.first + Integer.toHexString(offset + i)
      client.asyncGet(flag, MyTranscoder.instance).addListener {
        if (!stopped) {
          try {
            val node = it.get() as DataNode?
            future.tryComplete(offset + i to node)
            if (node != null) {
              client.delete(flag)
              handler.handle(node.buffer)
            }
          } catch (e: Throwable) {
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
        val list = it.result().list<Pair<Int, DataNode?>>().filter { it.second != null }.sortedBy { it.first }
        if (list.isNotEmpty()) {
          if (tempOffsetRead.get() < list.last().first)
            tempOffsetRead.set(list.last().first)
          if(lag > 10* delay){
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
          if (wnd.get() >= cWnd)
            wnd.set(DEFAULT)
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
    client.set(id + flagPair.second + Integer.toHexString(offset), 1000, DataNode(data), MyTranscoder.instance)
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
    client.set(id, 600, DataNode.shutdown, MyTranscoder.instance)
    vertx.cancelTimer(timerId)
  }

  fun start() {
    println("[${Date()}] Connection[$id] started")
    client.set(id, 600, DataNode.success, MyTranscoder.instance)
  }
}
