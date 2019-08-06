package co.zzyun.wsocks.data

import io.vertx.core.buffer.Buffer

class ConnectSuccess(userInfo: UserInfo,buffer: Buffer) {
  val uuid = String(Aes.decrypt(buffer.getBytes(Int.SIZE_BYTES,buffer.length()),userInfo.key,true))
  companion object {
    fun create(userInfo: UserInfo,uuid:String):Buffer {
      val encryptedUUID = Aes.encrypt(uuid.toByteArray(),userInfo.key,true)
      return Buffer.buffer()
        .appendIntLE(Flag.CONNECT_SUCCESS.ordinal)
        .appendBytes(encryptedUUID)
    }
  }
}
