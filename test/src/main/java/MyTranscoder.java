import net.spy.memcached.CachedData;
import net.spy.memcached.transcoders.Transcoder;

public class MyTranscoder implements Transcoder<String> {
  public static MyTranscoder instance = new MyTranscoder();
  @Override
  public boolean asyncDecode(CachedData d) {
    return false;
  }

  @Override
  public CachedData encode(String o) {
    return new CachedData(0,o.getBytes(),1500);
  }

  @Override
  public String decode(CachedData d) {
    return new String(d.getData());
  }

  @Override
  public int getMaxSize() {
    return 1500;
  }
}
