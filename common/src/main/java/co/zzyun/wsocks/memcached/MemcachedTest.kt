package co.zzyun.wsocks.memcached

import io.vertx.core.buffer.Buffer
import net.spy.memcached.MemcachedClient
import org.apache.commons.lang3.RandomUtils
import java.net.InetSocketAddress

fun main() {
  val client = MemcachedClient(InetSocketAddress("20.188.10.6", 11211))
  client.set("test", 10, DataNode(Buffer.buffer(RandomUtils.nextBytes(1000))), MyTranscoder.instance)
  println(client.get("test", MyTranscoder.instance).buffer.toString())
  for (i in 0..16) {
    Thread.sleep(50)
    client.asyncGet("test", MyTranscoder.instance).addListener {
      println((it.get() as DataNode).buffer.toString())
    }
  }

}
