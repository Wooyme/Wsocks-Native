import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;
import org.apache.commons.lang3.RandomStringUtils;

public class Main {
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    RedisClient client = RedisClient.create(vertx,new RedisOptions().setHost("103.101.179.233").setPort(6379));
    String key = RandomStringUtils.randomAlphanumeric(8);
    client.setBinary(key, Buffer.buffer(RandomStringUtils.randomAlphanumeric(1000)),r->{
      if(r.failed()){
        r.cause().printStackTrace();
        return;
      }
      client.getBinary(key,r1->{
        if(r1.failed()){
          r1.cause().printStackTrace();
          return;
        }
        System.out.println(r1.result().toString());
      });
    });
  }
}
