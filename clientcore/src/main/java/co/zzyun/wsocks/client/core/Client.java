package co.zzyun.wsocks.client.core;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;

public class Client {
  public static void main(String[] args) {
    System.setProperty("java.net.preferIPv4Stack","true");
    System.setProperty("vertx.disableDnsResolver","true");
    Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1)
      .setWorkerPoolSize(1)
      .setInternalBlockingPoolSize(1));
    EventBus eventBus = vertx.eventBus();
    vertx.createHttpServer().requestHandler(req->{
      if(!req.params().isEmpty()) {
        String host = req.getParam("host");
        int port = Integer.valueOf(req.getParam("port"));
        String user = req.getParam("user");
        String pass = req.getParam("pass");
        eventBus.request("config-modify", new Property(host, port, user, pass).toJson(), event -> {
          if (event.failed()) {
            req.response().setStatusCode(500).setStatusMessage(event.cause().getLocalizedMessage()).end();
          } else {
            req.response().setStatusCode(200).end();
          }
        });
      }else{
        eventBus.request("status",null,event->{
          if(event.failed())
            req.response().setStatusCode(500).setStatusMessage(event.cause().getLocalizedMessage()).end();
          else {
            if(((String)event.result().body()).isEmpty())
              req.response().setStatusCode(201).end();
            else
              req.response().setStatusCode(200).end();
          }
        });
      }
    }).listen(1088,r->{
      if(r.failed()) r.cause().printStackTrace();
      else System.out.println("Listen at 1088");
    });
    vertx.deployVerticle(new ClientWebSocket());
  }
}
