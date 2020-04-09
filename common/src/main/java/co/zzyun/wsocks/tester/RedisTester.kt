package co.zzyun.wsocks.tester

import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.redis.RedisClient
import io.vertx.redis.RedisOptions
import org.apache.commons.lang3.RandomStringUtils
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import java.util.function.Consumer

class RedisTester(private val vertx:Vertx):Tester {
  override fun test(ips: List<String>): Future<String> {
    val finalFut = Future.future<String>()
    val dataList: MutableList<Data> = ArrayList()
    val runnableList: Queue<Runnable> = LinkedBlockingDeque()
    ips.forEach { finalLine->
      runnableList.add(Runnable {
        val client = RedisClient.create(vertx, RedisOptions().setHost(finalLine).setPort(6379))
        println("-----------------------")
        val key = RandomStringUtils.randomAlphanumeric(8)
        val beginTime = Date()
        val futList: MutableList<Future<*>> = LinkedList()
        for (i in 0..63) {
          val fut = Future.future<Void>()
          futList.add(fut)
          client.setBinary(key, Buffer.buffer(RandomStringUtils.randomAlphanumeric(1200))) { r: AsyncResult<Void?> ->
            if (r.failed()) {
              fut.fail("$finalLine:无法写入")
              return@setBinary
            }
            client.getBinary(key) { r1: AsyncResult<Buffer?> ->
              if (r1.failed()) {
                fut.fail("$finalLine:无法读取")
              } else {
                fut.complete()
              }
            }
          }
        }
        val fut: Future<*> = CompositeFuture.all(futList).setHandler { res: AsyncResult<CompositeFuture?> ->
          if (res.succeeded()) {
            val lag = (Date().time - beginTime.time).toInt()
            println("$finalLine:$lag")
            dataList.add(Data(finalLine, lag))
          } else {
            println(res.cause().message)
          }
          client.close { }
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
        vertx.setTimer(500) { t: Long? -> fut.tryFail("$finalLine:超时") }
      })
    }
    runnableList.poll().run()
    return finalFut
  }
}
