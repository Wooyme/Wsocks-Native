package co.zzyun.wsocks.server.api

import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient

class CenterApi(private val centerPort: Int, private val centerHost: String, private val webClient: WebClient) {
  private lateinit var serverToken: String
  private fun getToken():Future<Void>{
    val future = Future.future<Void>()
    webClient.post(centerPort, centerHost, "/wp-json/jwt-auth/v1/token")
      .sendJsonObject(JsonObject().put("username", "wooyme").put("password", "wy16880175")) {
        if (it.failed()) {
          it.cause().printStackTrace()
          future.fail(it.cause())
        } else {
          serverToken = it.result().bodyAsJsonObject().getString("token")
          println("Connected to center!")
          future.complete()
        }
      }
    return future
  }

  fun validate(token: String):Future<Pair<ByteArray,JsonObject>> {
    val future = Future.future<Pair<ByteArray,JsonObject>>()
    webClient.get(centerPort, centerHost, "/wp-json/wp/v2/users/me").putHeader("Authorization", "Bearer $token").send {
      if (it.failed()) {
        it.cause().printStackTrace()
      } else {
        if(it.result().statusCode()==200){
          val json = it.result().bodyAsJsonObject()
          val name = json.getString("name")
          val array = name.toByteArray()
          val key = if (16 > array.size)
            array + ByteArray(16 - array.size) { 0x06 }
          else
            array
          future.complete(key to JsonObject())
        }else{
          future.fail(it.result().statusMessage())
        }
      }
    }
    return future
  }

  fun online(id: String, host: String, port: Int) {
    if(this::serverToken.isInitialized) {
      webClient.post(centerPort, centerHost, "/wp-json/wp/v2/posts/$id")
        .putHeader("Authorization", "Bearer $serverToken")
        .sendJsonObject(JsonObject().put("content", "$host/$port")) {
          if (it.failed()) {
            it.cause().printStackTrace()
          }else{
            if(it.result().statusCode()!=200){
              getToken().setHandler {
                if(it.succeeded())
                  online(id,host,port)
              }
            }
          }
        }
    }else{
      getToken().setHandler {
        if(it.succeeded()){
          online(id,host,port)
        }
      }
    }
  }

  fun update(token:String,usage:Long){

  }

  fun leave(token:String){

  }
}

