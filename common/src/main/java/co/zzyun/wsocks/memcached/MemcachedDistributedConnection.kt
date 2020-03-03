package co.zzyun.wsocks.memcached

import com.spotify.folsom.MemcacheClient
import com.spotify.folsom.MemcacheClientBuilder
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class MemcachedDistributedConnection(private val vertx: Vertx, private val center: MemcacheClient<ByteArray>
                                     , private val id: String, private val nodeList: List<String>, val info: JsonObject, private val flagPair: Pair<String, String> = ("c" to "s")) {
  class ConnectionEntry(val client: MemcacheClient<ByteArray>, var offsetRead: Int, var wnd: Int, var tempOffsetRead: Int, val offsetWrite: AtomicInteger = AtomicInteger(0))

  private lateinit var handler: Handler<Buffer>
  private lateinit var clients: List<ConnectionEntry>
  private var timerId = 0L
  private val random = Random(Date().time)

  fun reject() {
    center.set(id, REJECT, 1000)
  }

  fun stop(){
    vertx.cancelTimer(timerId)
    clients.forEach {
      it.client.shutdown()
    }
  }

  fun connect() {
    center.set(id,SUCCESS,1000)
    clients = nodeList.map {
      ConnectionEntry(MemcacheClientBuilder.newByteArrayClient().withAddress(it, 11211).connectBinary(), 0, DEFAULT_WND, 0)
    }
    timerId = vertx.setPeriodic(50) {
      clients.forEach { entry ->
        val cWnd = entry.wnd
        (0..cWnd).map { i ->
          val future = Future.future<Pair<Int, ByteArray?>>()
          val flag = id + flagPair.first + Integer.toHexString(entry.offsetRead + i)
          entry.client.get(flag).handle { r, u ->
            entry.client.delete(flag)
            if(r!=null)
              handler.handle(Buffer.buffer(r))
            future.complete(i to r)
          }
          future
        }.let {
          CompositeFuture.all(it).setHandler {
            val list = it.result().list<Pair<Int, ByteArray?>>().filter { it.second != null }.sortedBy { it.first }
            if (list.isNotEmpty()) {
              if (entry.tempOffsetRead < list.last().first)
                entry.tempOffsetRead = list.last().first
              if (list.size >= entry.wnd) {
                if (entry.wnd * 2 <= 8)
                  entry.wnd *= 2
              } else if (entry.wnd >= cWnd && list.size < entry.wnd / 2) {
                entry.wnd = DEFAULT_WND
              }
            } else {
              if (entry.wnd >= cWnd)
                entry.wnd = DEFAULT_WND
              if (entry.offsetRead > entry.tempOffsetRead)
                entry.offsetRead = entry.tempOffsetRead
            }
            println("Wnd: $cWnd,Cur: ${list.size}")
          }
        }
        entry.offsetRead += cWnd
      }
    }
  }

  fun handler(handler: Handler<Buffer>): MemcachedDistributedConnection {
    this.handler = handler
    return this
  }

  fun write(buffer:Buffer){
    val entry = clients[random.nextInt(clients.size)]
    val offset = entry.offsetWrite.addAndGet(1)
    entry.client.set(id + flagPair.second + Integer.toHexString(offset),buffer.bytes,600)
  }

  companion object {
    private val REJECT = Buffer.buffer("reject").bytes
    private val SUCCESS = Buffer.buffer("success").bytes
    private const val DEFAULT_WND = 2

  }
}
