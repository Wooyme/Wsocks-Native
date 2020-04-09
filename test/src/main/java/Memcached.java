import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import net.spy.memcached.BinaryConnectionFactory;
import net.spy.memcached.MemcachedClient;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Memcached {
  public static void main(String[] args) throws IOException {
    System.setProperty("net.spy.log.LoggerImpl",
      "net.spy.memcached.compat.log.SunLogger");
    Logger.getLogger("net.spy.memcached").setLevel(Level.SEVERE);
    final List<Data> dataList = new ArrayList<>();
    Vertx vertx = Vertx.vertx();
    File file = new File("iplist.txt");
    Queue<Runnable> runnableList = new LinkedBlockingDeque<>();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = br.readLine()) != null) {
        String finalLine = line;
        runnableList.add(()->{
          try {
            List<InetSocketAddress> list = new ArrayList<>();
            list.add(new InetSocketAddress(finalLine, 11211));
            MemcachedClient client = new MemcachedClient(new BinaryConnectionFactory(), list);
            System.out.println("-----------------------");
            String key = RandomStringUtils.randomAlphanumeric(8);
            Date beginTime = new Date();
            List<Future> futList = new LinkedList<>();
            for (int i = 0; i < 4; i++) {
              Future<Void> fut = Future.future();
              futList.add(fut);
              client.set(key, 1000, Buffer.buffer(RandomStringUtils.randomAlphanumeric(1200)), MyTranscoder.instance).addListener(f -> {
                if(!f.getStatus().isSuccess()){
                  fut.tryFail(finalLine+":无法写入");
                  return;
                }
                client.asyncGet(key, MyTranscoder.instance).addListener(f1 -> {
                  if(f1.get()==null){
                    fut.tryFail(finalLine+":无法读取");
                  }else{
                    fut.tryComplete();
                  }
                });
              });
            }
            Future fut = CompositeFuture.all(futList).setHandler(res -> {
              if (res.succeeded()) {
                int lag = (int)((new Date().getTime())-beginTime.getTime());
                System.out.println(finalLine+":"+lag);
                dataList.add(new Data(finalLine,lag));
              } else {
                System.out.println(res.cause().getMessage());
              }
              client.shutdown();
              Runnable runnable = runnableList.poll();
              if (runnable != null) {
                runnable.run();
              }else{
                dataList.sort(Comparator.comparingInt(d -> d.lag));
                dataList.forEach(v-> System.out.println(v.toString()));
              }
            });
            vertx.setTimer(1000, t -> {
              fut.tryFail(finalLine + ":超时");
            });
          }catch (IOException e){
            System.out.println(finalLine+":无法连接");
            Runnable runnable = runnableList.poll();
            if (runnable != null) {
              runnable.run();
            }
          }
        });
      }
    }
    runnableList.poll().run();
  }
}
