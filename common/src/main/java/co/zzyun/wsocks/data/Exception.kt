package co.zzyun.wsocks.data

import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject

class Exception(userInfo: UserInfo,buffer: Buffer) {
  private val json = Buffer.buffer(Aes.decrypt(buffer.getBytes(Int.SIZE_BYTES,buffer.length()),userInfo.key,true)).toJsonObject()
  val message get() = json.getString("message")
  val uuid get() = json.getString("uuid")
  companion object {
    fun create(userInfo: UserInfo,uuid:String,message:String):Buffer {
      val encryptedBuffer = Aes.encrypt(JsonObject()
          .put("message", message)
          .put("uuid", uuid)
          .toBuffer().bytes,userInfo.key,true)
      return Buffer.buffer().appendIntLE(Flag.EXCEPTION.ordinal).appendBytes(encryptedBuffer)
    }
  }
}
