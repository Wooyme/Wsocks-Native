package co.zzyun.wsocks.client.core;

import co.zzyun.wsocks.client.core.client.BaseClient;
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
    System.setProperty("java.net.preferIPv4Stack", "true");
    System.setProperty("vertx.disableDnsResolver", "true");
    System.setProperty("io.netty.noUnsafe", "true");
    Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1)
      .setWorkerPoolSize(2)
      .setInternalBlockingPoolSize(1)
      .setFileSystemOptions(new FileSystemOptions()
        .setFileCachingEnabled(false)
        .setClassPathResolvingEnabled(false)));
    EventBus eventBus = vertx.eventBus();
    vertx.createHttpServer().requestHandler(req -> {
      switch (req.path()) {
        case "/start": {
          if (!deployId.isEmpty()) {
            vertx.undeploy(deployId);
          }
          String centerHost = req.getParam("center_host");
          String centerPort = req.getParam("center_port");
          String user = req.getParam("user");
          String pass = req.getParam("pass");
          BaseClient client = new BaseClient();
          vertx.deployVerticle(client, new DeploymentOptions().setConfig(new JsonObject()
            .put("center.host", centerHost)
            .put("center.port", Integer.valueOf(centerPort))
            .put("user.info", new JsonObject().put("user", user).put("pass", pass))), result -> {
            if (result.failed())
              req.response().setStatusCode(500).setStatusMessage(result.cause().getLocalizedMessage()).end();
            else {
              deployId = result.result();
              req.response().setStatusCode(200).end();
            }
          });
        }
        break;
        case "/connect": {
          vertx.eventBus().send("client-connect", new JsonObject().put("host", req.getParam("host"))
            .put("port", Integer.valueOf(req.getParam("port"))), new DeliveryOptions().setSendTimeout(120 * 1000), (r) -> {
            if (r.failed()) {
              req.response().setStatusCode(500).setStatusMessage(r.cause().getLocalizedMessage()).end();
            } else {
              req.response().end();
            }
          });
        }
        break;
        case "/status": {
          if (deployId.isEmpty()) {
            req.response().setStatusCode(201).end();
            return;
          }
          eventBus.send("status", null, new DeliveryOptions().setSendTimeout(120 * 1000), event -> {
            if (event.failed())
              req.response().setStatusCode(500).setStatusMessage(event.cause().getLocalizedMessage()).end();
            else {
              if (((String) event.result().body()).isEmpty())
                req.response().setStatusCode(202).end();
              else
                req.response().setStatusCode(200).end();
            }
          });
        }
        break;
      }
    }).listen(1078, r -> {
      if (r.failed()) r.cause().printStackTrace();
      else System.out.println("Listen at 1078");
    });
  }
}
