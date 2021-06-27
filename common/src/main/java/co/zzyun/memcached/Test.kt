package co.zzyun.memcached

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import org.apache.commons.lang3.RandomStringUtils
import java.nio.charset.Charset

fun main(){
  val vertx = Vertx.vertx()
  MemcachedClient.connect(vertx,11211,"23.251.36.160", Handler {
    if(it.succeeded()){
      val client = it.result()
      val key = RandomStringUtils.random(16)
      client.set(key, Buffer.buffer(RandomStringUtils.randomAlphanumeric(1200)), Handler {
        if(it.succeeded()){
          client.get(key, Handler {
            if(it.succeeded()){
              println(it.result().toString(Charset.defaultCharset()))
            }else{
              it.cause().printStackTrace()
            }
          })
          client.delete(key)
          vertx.setTimer(1000){
            client.get(key, Handler {
              if(it.succeeded()){
                println(it.result().toString(Charset.defaultCharset()))
              }else{
                it.cause().printStackTrace()
              }
            })
          }
        }else{
          it.cause().printStackTrace()
        }
      })
    }else{
      it.cause().printStackTrace()
    }
  })
}
