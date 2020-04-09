package co.zzyun.wsocks.tester

import co.zzyun.memcached.MemcachedClient
import co.zzyun.wsocks.memcached.DataNode
import co.zzyun.wsocks.memcached.MyTranscoder
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import org.apache.commons.lang3.RandomStringUtils
import java.io.IOException
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.Logger

class MemcachedTester(private val vertx: Vertx):Tester {
  init {
    System.setProperty("net.spy.log.LoggerImpl",
      "net.spy.memcached.compat.log.SunLogger")
    Logger.getLogger("net.spy.memcached").level = Level.SEVERE
  }
  override fun test(ips: List<String>): Future<String> {
    val finalFut = Future.future<String>()
    val dataList: MutableList<Data> = ArrayList()
    val runnableList: Queue<Runnable> = LinkedBlockingDeque()
    ips.forEach {ip->
      runnableList.add(Runnable {
          val list: MutableList<InetSocketAddress> = ArrayList()
          list.add(InetSocketAddress(ip, 11211))
          MemcachedClient.connect(vertx,11211,ip, Handler {
            if(it.succeeded()){
              val client = it.result()
              println("-----------------------")
              val key = RandomStringUtils.randomAlphanumeric(8)
              val beginTime = Date()
              val futList: MutableList<Future<*>> = LinkedList()
              for (i in 0..63) {
                val fut = Future.future<Void>()
                futList.add(fut)
                client.set(key, Buffer.buffer(RandomStringUtils.randomAlphanumeric(1200)), Handler{ f->
                  if (f.failed()) {
                    fut.tryFail("$ip:无法写入")
                    return@Handler
                  }
                  client.get(key, Handler{ f1->
                    if (f1.result()==null) {
                      fut.tryFail("$ip:无法读取")
                    } else {
                      fut.tryComplete()
                    }
                  })
                })
              }
              val fut: Future<*> = CompositeFuture.all(futList).setHandler { res: AsyncResult<CompositeFuture?> ->
                if (res.succeeded()) {
                  val lag = (Date().time - beginTime.time).toInt()
                  println("$ip:$lag")
                  dataList.add(Data(ip, lag))
                } else {
                  println(res.cause().message)
                }
                client.close()
                val runnable = runnableList.poll()
                if (runnable != null) {
                  runnable.run()
                } else {
                  dataList.sortWith(Comparator.comparingInt { d: Data -> d.lag })
                  dataList.forEach{ v: Data -> println(v.toString()) }
                  if(dataList.size>0)
                    finalFut.tryComplete(dataList[0].ip)
                  else
                    finalFut.tryFail("No available node")
                }
              }
              vertx.setTimer(500) { fut.tryFail("$ip:超时") }
            }else{
              val runnable = runnableList.poll()
              if (runnable != null) {
                runnable.run()
              } else {
                dataList.sortWith(Comparator.comparingInt { d: Data -> d.lag })
                dataList.forEach(Consumer { v: Data -> println(v.toString()) })
                if(dataList.size>0)
                  finalFut.tryComplete(dataList[0].ip)
                else
                  finalFut.tryFail("No available node")
              }
            }
          })
      })
    }
    runnableList.poll().run()
    return finalFut
  }

}
