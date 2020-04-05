import io.vertx.core.Vertx;
import io.vertx.ext.amqp.*;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

public class AMQP {
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    AmqpClientOptions options = new AmqpClientOptions()
      .setHost("103.74.172.196")
      .setPort(5672);
    AmqpClient client = AmqpClient.create(vertx, options);
    AtomicLong beginTime = new AtomicLong();
    client.connect(res->{
      if(res.failed()) res.cause().printStackTrace();
      else System.out.println("OK");
      res.result().createReceiver("my-queue",done->{
        if (done.failed()) {
          System.out.println("Unable to create receiver");
        } else {
          AmqpReceiver receiver = done.result();
          receiver
            .exceptionHandler(t -> {
              // Error thrown.
            })
            .handler(msg -> {
              System.out.println(new Date().getTime()- beginTime.get());
              System.out.println(msg.bodyAsString());
            });
        }
      });
      res.result().createSender("my-queue", done -> {
        if (done.failed()) {
          System.out.println("Unable to create a sender");
        } else {
          AmqpSender result = done.result();
          vertx.setTimer(1000,t->{
            for (int i = 0; i < 64; i++) {
              result.send(AmqpMessage.create().withBody(RandomStringUtils.randomAlphanumeric(1200)).build());
            }
            beginTime.set(new Date().getTime());
          });
        }
      });
    });
  }
}
