import io.vertx.core.buffer.Buffer;
import net.spy.memcached.CachedData;
import net.spy.memcached.transcoders.Transcoder;

public class MyTranscoder implements Transcoder<Buffer> {
  public static MyTranscoder instance = new MyTranscoder();
  @Override
  public boolean asyncDecode(CachedData d) {
    return false;
  }

  @Override
  public CachedData encode(Buffer o) {
    return new CachedData(0,o.getBytes(),1500);
  }

  @Override
  public Buffer decode(CachedData d) {
    return Buffer.buffer(d.getData());
  }

  @Override
  public int getMaxSize() {
    return 1500;
  }
}
