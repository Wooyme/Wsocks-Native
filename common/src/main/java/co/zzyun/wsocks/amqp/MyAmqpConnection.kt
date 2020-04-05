package co.zzyun.wsocks.amqp

import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.amqp.AmqpClient
import io.vertx.ext.amqp.AmqpMessage
import io.vertx.ext.amqp.AmqpReceiver
import io.vertx.ext.amqp.AmqpSender

class MyAmqpConnection(private val client:AmqpClient,val id:String,val header:JsonObject) {
  private lateinit var receiver:AmqpReceiver
  private lateinit var sender:AmqpSender
  private lateinit var handler:(Buffer)->Any
  private lateinit var exceptionHandler:(Throwable)->Any

  fun reject(){
    client.createSender(id){
      if(it.succeeded()){
        it.result().send(AmqpMessage.create().withJsonObjectAsBody(JsonObject().put("status",-1)).build())
      }
    }
  }

  fun connect():Future<MyAmqpConnection>{
    val fut = Future.future<MyAmqpConnection>()
    val fut1= Future.future<AmqpSender>()
    val fut2 = Future.future<AmqpReceiver>()
    client.createSender(id){
      if(it.succeeded())
        fut1.complete(it.result())
      else
        fut1.fail(it.cause())
    }
    client.createReceiver(id){
      if(it.succeeded())
        fut2.complete(it.result())
      else
        fut2.fail(it.cause())
    }
    CompositeFuture.all(fut1,fut2).setHandler {
      if(it.failed()) fut.fail(it.cause())
      else {
        val list = it.result().list<Any>()
        sender = list[0] as AmqpSender
        sender.exceptionHandler {
          this.exceptionHandler(it)
        }
        receiver = list[1] as AmqpReceiver
        receiver.handler {
          this.handler(it.bodyAsBinary())
        }.exceptionHandler {
          this.exceptionHandler(it)
        }
        fut.complete(this)
      }
    }
    return fut
  }

  fun handler(handler:(Buffer)->Any){
    this.handler = handler
  }

  fun exceptionHandler(handler:(Throwable)->Any){
    this.exceptionHandler = handler
  }

  fun send(buffer:Buffer){
    this.sender.send(AmqpMessage.create().withBufferAsBody(buffer).build())
  }
  fun stop(){
    if(this::sender.isInitialized)
      this.sender.close {  }
    if(this::receiver.isInitialized)
      this.receiver.close{}
  }
}
