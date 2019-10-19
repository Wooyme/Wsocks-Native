package co.zzyun.wsocks.data

import io.vertx.core.json.JsonObject
import org.apache.commons.codec.digest.DigestUtils

class UserInfo(val username:String,val password:String,val key:ByteArray,val maxLoginDevices:Int=-1,val limitation:Long = -1L){
  val secret by lazy {
    DigestUtils.md5Hex("1"+this.username+this.password)
  }
  companion object {
    fun fromJson(json:JsonObject): UserInfo {
      val array = json.getString("pass").toByteArray()
      val key = if (16 > array.size)
        array + ByteArray(16 - array.size) { 0x06 }
      else
        array
      return UserInfo(json.getString("user")
          ,json.getString("pass")
          ,key
          ,json.getInteger("multiple")?:-1
          ,json.getLong("limit")?:-1L)
    }
  }
}
