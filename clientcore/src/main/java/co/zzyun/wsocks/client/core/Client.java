package co.zzyun.wsocks.client.core;

import co.zzyun.wsocks.client.core.client.AbstractClient;
import co.zzyun.wsocks.client.core.client.FullUdp;
import co.zzyun.wsocks.client.core.client.HttpUdp;
import co.zzyun.wsocks.client.core.client.WebsocketUdp;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.json.JsonObject;

public class Client {
  private static String deployId = "";
  public static void main(String[] args) {
    System.setProperty("java.net.preferIPv4Stack","true");
    System.setProperty("vertx.disableDnsResolver","true");
    System.setProperty("io.netty.noUnsafe","true");
    Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1)
      .setWorkerPoolSize(2)
      .setInternalBlockingPoolSize(1)
      .setFileSystemOptions(new FileSystemOptions()
        .setFileCachingEnabled(false)
        .setClassPathResolvingEnabled(false)));
    EventBus eventBus = vertx.eventBus();
    vertx.createHttpServer().requestHandler(req->{
      if(!req.params().isEmpty()) {
        if(!deployId.isEmpty()){
          vertx.undeploy(deployId);
        }
        String host = req.getParam("host");
        int port = Integer.valueOf(req.getParam("port"));
        String user = req.getParam("user");
        String pass = req.getParam("pass");
        AbstractClient client;
        switch (System.getProperty("wsocks.client")){
          case "http":
            client = new HttpUdp();
            break;
          case "udp":
            client = new FullUdp();
            break;
          default:
            client = new WebsocketUdp();
        }
        vertx.deployVerticle(client,new DeploymentOptions().setConfig(new JsonObject()
          .put("remote.port",port)
          .put("remote.host",host)
          .put("user.info",new JsonObject().put("user",user).put("pass",pass))),result->{
            if(result.failed()) req.response().setStatusCode(500).setStatusMessage(result.cause().getLocalizedMessage()).end();
            else{
              deployId = result.result();
              req.response().setStatusCode(200).end();
            }
        });
      }else{
        eventBus.send("status",null,new DeliveryOptions().setSendTimeout(120*1000),event->{
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
    }).listen(1078,r->{
      if(r.failed()) r.cause().printStackTrace();
      else System.out.println("Listen at 1078");
    });
  }
}
