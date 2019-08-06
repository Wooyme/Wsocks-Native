package co.zzyun.wsocks.data

import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject

class ClientConnect(userInfo: UserInfo, buffer: Buffer) {
  private val json = Buffer.buffer(Aes.decrypt(buffer.getBytes(Int.SIZE_BYTES,buffer.length()),userInfo.key,true)).toJsonObject()
  val host get() = json.getString("host")
  val port get() = json.getInteger("port")
  val uuid get() = json.getString("uuid")

  companion object {
    fun create(userInfo: UserInfo,uuid:String,host: String, port: Int): Buffer {
      val buffer = Buffer.buffer()
        .appendBuffer(JsonObject().put("host", host).put("port", port).put("uuid", uuid).toBuffer())
      return Buffer.buffer()
          .appendIntLE(Flag.CONNECT.ordinal).appendBytes(Aes.encrypt(buffer.bytes,userInfo.key,true))
    }
  }
}
