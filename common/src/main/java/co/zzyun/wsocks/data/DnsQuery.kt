package co.zzyun.wsocks.data

import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject

class DnsQuery(key:ByteArray,buffer: Buffer) {
  private val json = Buffer.buffer(Aes.decrypt(buffer.getBytes(Int.SIZE_BYTES,buffer.length()),key,true)).toJsonObject()
  val host = json.getString("host")
  val uuid get() = json.getString("uuid")
  companion object {
    fun create(key:ByteArray,uuid:String,host: String): Buffer {
      val buffer = Buffer.buffer()
          .appendBuffer(JsonObject().put("host", host).put("uuid", uuid).toBuffer())
      val encryptedBuffer = Aes.encrypt(buffer.bytes,key,true)
      return Buffer.buffer()
          .appendIntLE(Flag.DNS.ordinal)
          .appendBytes(encryptedBuffer)
    }
  }
}
