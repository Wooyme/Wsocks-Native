package co.zzyun.wsocks.memcached

import io.vertx.core.buffer.Buffer

class DataNode(val buffer: Buffer){
  fun isSuccess():Boolean{
    return buffer.length()==Short.SIZE_BYTES && buffer.getShort(0) == ZERO
  }
  fun isShutdown():Boolean{
    return buffer.length()==Short.SIZE_BYTES && buffer.getShort(0) == ONE
  }
  fun isReject():Boolean{
    return buffer.length()==Short.SIZE_BYTES && buffer.getShort(0) == TWO
  }
  companion object {
    private const val ZERO = 0.toShort()
    private const val ONE = 1.toShort()
    private const val TWO = 2.toShort()
    val shutdown = DataNode(Buffer.buffer().appendShort(1))
    val success = DataNode(Buffer.buffer().appendShort(0))
    val reject = DataNode(Buffer.buffer().appendShort(2))
  }
}
