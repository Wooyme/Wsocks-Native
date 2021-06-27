import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;

public class Redis {
  public static void main(String[] args) throws IOException {
    final List<Data> dataList = new ArrayList<>();
    Vertx vertx = Vertx.vertx();
    File file = new File("iplist.txt");
    Queue<Runnable> runnableList = new LinkedBlockingDeque<>();

    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = br.readLine()) != null) {
        String finalLine = line;
        runnableList.add(()->{
          RedisClient client = RedisClient.create(vertx,new RedisOptions().setHost(finalLine).setPort(6379));
          System.out.println("-----------------------");
          String key = RandomStringUtils.randomAlphanumeric(8);
          Date beginTime = new Date();
          List<Future> futList= new LinkedList<>();
          for (int i = 0; i < 32; i++) {
            Future<Void> fut = Future.future();
            futList.add(fut);
            client.setBinary(key, Buffer.buffer(RandomStringUtils.randomAlphanumeric(1200)),r->{
              if(r.failed()){
                fut.fail(finalLine+":无法写入");
                return;
              }
              client.getBinary(key,r1->{
                if(r1.failed()){
                  fut.fail(finalLine+":无法读取");
                }else{
                  fut.complete();
                }
              });
            });
          }
          Future fut = CompositeFuture.all(futList).setHandler(res->{
            if(res.succeeded()){
              int lag = (int)((new Date().getTime())-beginTime.getTime());
              System.out.println(finalLine+":"+lag);
              dataList.add(new Data(finalLine,lag));
            }else{
              System.out.println(res.cause().getMessage());
            }
            client.close(r->{ });
            Runnable runnable = runnableList.poll();
            if(runnable!=null){
              runnable.run();
            }else{
              dataList.sort(Comparator.comparingInt(d -> d.lag));
              dataList.forEach(v-> System.out.println(v.toString()));
            }
          });
          vertx.setTimer(1000,t-> fut.tryFail(finalLine+":超时"));
        });
      }
    }
    runnableList.poll().run();
  }
}
