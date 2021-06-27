package co.zzyun.wsocks.server.receiver

import co.zzyun.wsocks.KCP
import co.zzyun.wsocks.amqp.MyAmqpConnection
import co.zzyun.wsocks.server.sender.AMQPSender
import io.vertx.core.Handler
import io.vertx.core.net.SocketAddress
import io.vertx.ext.amqp.AmqpClient
import io.vertx.ext.amqp.AmqpClientOptions

class AMQPReceiver: AbstractLongConnectionReceiver<MyAmqpConnection>() {
  private lateinit var client:AmqpClient
  override fun initServer(handler: Handler<MyAmqpConnection>) {
    senderMap["amqp"] = AMQPSender()
    val options = AmqpClientOptions()
      .setHost("host")
      .setPort(5672)
    client = AmqpClient.create(vertx, options)
    client.connect {
      if(it.succeeded()){
        it.result().createReceiver(id){
          if(it.failed()){
            println(it.cause().localizedMessage)
          }else{
            it.result().handler {
              val json = it.bodyAsJsonObject()
              handler.handle(MyAmqpConnection(client,json.getString("id"),json))
            }.exceptionHandler {

            }
          }
        }
      }else{
        println(it.cause().localizedMessage)
      }
    }
  }

  override fun handleLogin(conn: MyAmqpConnection): LoginInfo? {
    return try {
      val info = conn.header
      val token = info.getString("token")
      val host = info.getString("host")
      val port = info.getInteger("port")
      val recvType = info.getString("recv")
      val conv = info.getLong("conv")
      LoginInfo(SocketAddress.inetSocketAddress(port, host), recvType, token, conv)
    }catch (e:Throwable){
      null
    }
  }

  override fun onConnected(conn: MyAmqpConnection, kcp: KCP) {
    conn.handler {
      kcp.InputAsync(it)
    }
    conn.exceptionHandler {
      it.printStackTrace()
    }
    conn.connect().setHandler {
      if(it.failed()) return@setHandler it.cause().printStackTrace()
    }
  }

  override fun onFailed(conn: MyAmqpConnection) {
    conn.reject()
  }

  override fun close(conn: MyAmqpConnection?, address: SocketAddress) {
    conn?.stop()
  }
}
