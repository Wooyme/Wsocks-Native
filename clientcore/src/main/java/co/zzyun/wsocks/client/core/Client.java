package co.zzyun.wsocks.client.core;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;

public class Client {
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1)
      .setWorkerPoolSize(1)
      .setInternalBlockingPoolSize(1));
    EventBus eventBus = vertx.eventBus();
    vertx.createHttpServer().requestHandler(req->{
      String host = req.getParam("host");
      int port = Integer.valueOf(req.getParam("port"));
      String user = req.getParam("user");
      String pass = req.getParam("pass");
      eventBus.send("config-modify",new Property(host,port,user,pass).toJson());
      req.response().end();
    }).listen(1088,r->{
      if(r.failed()) r.cause().printStackTrace();
      else System.out.println("Listen at 1088");
    });
    vertx.deployVerticle(new ClientWebSocket());
  }
}
