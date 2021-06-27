import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import net.spy.memcached.BinaryConnectionFactory;
import net.spy.memcached.MemcachedClient;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class MultiMemcached {
  public static void main(String[] args) throws IOException {
    Vertx vertx = Vertx.vertx();
    List<InetSocketAddress> list1 = new ArrayList<>();
    list1.add(new InetSocketAddress("159.138.43.209", 11211));
    MemcachedClient client1 = new MemcachedClient(new BinaryConnectionFactory(), list1);
    List<InetSocketAddress> list2 = new ArrayList<>();
    list2.add(new InetSocketAddress("154.210.20.67", 11211));
    MemcachedClient client2 = new MemcachedClient(new BinaryConnectionFactory(), list2);
    Date beginTime1 = new Date();
    test1(client1).setHandler(r -> {
      System.out.println(new Date().getTime() - beginTime1.getTime());
      vertx.setTimer(2*1000,l->{
        Date beginTime2 = new Date();
        test1(client2).setHandler(r1 ->{
          System.out.println(new Date().getTime() - beginTime2.getTime());
          vertx.setTimer(2*1000,l1->{
            Date beginTime3 = new Date();
            test2(client1,client2).setHandler(r2 -> System.out.println(new Date().getTime() - beginTime3.getTime()));
          });
        });
      });
    });
  }

  private static Future test1(MemcachedClient client){
    List<Future> futList = new LinkedList<>();
    for (int i = 0; i < 128; i++) {
      Future fut = Future.future();
      futList.add(fut);
      String key = RandomStringUtils.randomAlphanumeric(8);
      client.set(key, 1000, Buffer.buffer(RandomStringUtils.randomAlphanumeric(1200)), MyTranscoder.instance).addListener(f -> {
        if(!f.getStatus().isSuccess()){
          fut.tryFail("无法写入");
          return;
        }
        client.asyncGet(key, MyTranscoder.instance).addListener(f1 -> {
          if(f1.get()==null){
            fut.tryFail("无法读取");
          }else{
            fut.tryComplete();
          }
        });
      });
    }
    return CompositeFuture.all(futList);
  }

  private static Future test2(MemcachedClient client1,MemcachedClient client2){
    List<Future> futList = new LinkedList<>();
    for (int i = 0; i < 64; i++) {
      Future fut1 = Future.future();
      futList.add(fut1);
      String key = RandomStringUtils.randomAlphanumeric(8);
      client1.set(key, 1000, Buffer.buffer(RandomStringUtils.randomAlphanumeric(1200)), MyTranscoder.instance).addListener(f -> {
        if(!f.getStatus().isSuccess()){
          fut1.tryFail("无法写入");
          return;
        }
        client1.asyncGet(key, MyTranscoder.instance).addListener(f1 -> {
          if(f1.get()==null){
            fut1.tryFail("无法读取");
          }else{
            fut1.tryComplete();
          }
        });
      });
      Future fut2 = Future.future();
      futList.add(fut2);
      client2.set(key, 1000, Buffer.buffer(RandomStringUtils.randomAlphanumeric(1200)), MyTranscoder.instance).addListener(f -> {
        if(!f.getStatus().isSuccess()){
          fut2.tryFail("无法写入");
          return;
        }
        client2.asyncGet(key, MyTranscoder.instance).addListener(f1 -> {
          if(f1.get()==null){
            fut2.tryFail("无法读取");
          }else{
            fut2.tryComplete();
          }
        });
      });
    }
    return CompositeFuture.all(futList);
  }
}
