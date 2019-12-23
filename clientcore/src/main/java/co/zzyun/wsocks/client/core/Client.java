package co.zzyun.wsocks.client.core;

import co.zzyun.wsocks.client.core.client.BaseClient;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;


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
    final EventBus eventBus = vertx.eventBus();
    final String index;
    try {
      InputStream is = Client.class.getResourceAsStream("/index.html");
      index = IOUtils.toString(is, Charset.defaultCharset());
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
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
              req.response().end(result.cause().getMessage());
            else {
              deployId = result.result();
              req.response().end();
            }
          });
        }
        break;
        case "/connect": {
          vertx.eventBus().send("client-connect", new JsonObject()
            .put("host", req.getParam("host"))
            .put("port", Integer.valueOf(req.getParam("port")))
            .put("type",req.getParam("type")), new DeliveryOptions().setSendTimeout(120 * 1000), (r) -> {
            if (r.failed()) {
              req.response().end(r.cause().getMessage());
            } else {
              req.response().end();
            }
          });
        }
        break;
        case "/status": {
          if (deployId.isEmpty()) {
            req.response().end("客户端未连接");
          }else {
            eventBus.send("status", null, new DeliveryOptions().setSendTimeout(120 * 1000), event -> req.response().setStatusCode(200).end((String) event.result().body()));
          }
        }
        break;
        case "/hosts":{
          if (deployId.isEmpty()) {
            req.response().end();
          }else {
            eventBus.send("hosts", null, new DeliveryOptions().setSendTimeout(120 * 1000), event -> req.response().setStatusCode(200).end(((JsonArray) event.result().body()).toBuffer()));
          }
        }
        break;
        default:{
          req.response().end(index);
        }
      }
    }).listen(1078, r -> {
      if (r.failed()) r.cause().printStackTrace();
      else System.out.println("Listen at 1078");
    });
  }
}
