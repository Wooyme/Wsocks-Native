package co.zzyun.wsocks.client.core;

import client.Tray;
import co.zzyun.wsocks.client.core.client.BaseClient;
import co.zzyun.wsocks.data.UserInfo;
import io.netty.channel.local.LocalAddress;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClientOptions;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;


public class Client {
  private static String deployId = "";
  public static BaseClient client;
  public static Vertx start() {
    System.setProperty("java.net.preferIPv4Stack", "true");
    System.setProperty("vertx.disableDnsResolver", "true");
    System.setProperty("io.netty.noUnsafe", "true");
    Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1)
      .setWorkerPoolSize(1)
      .setInternalBlockingPoolSize(1)
      .setFileSystemOptions(new FileSystemOptions()
        .setFileCachingEnabled(false)
        .setClassPathResolvingEnabled(false)).setBlockedThreadCheckInterval(100000000000L));
    final String pac;
    try {
      InputStream is = Client.class.getResourceAsStream("/gfwlist.pac");
      pac = IOUtils.toString(is, Charset.defaultCharset());
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    final String global;
    try {
      InputStream is = Client.class.getResourceAsStream("/global.pac");
      global = IOUtils.toString(is, Charset.defaultCharset());
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    vertx.createHttpServer().requestHandler(req -> {
      if(req.method()== HttpMethod.OPTIONS){
        req.response().putHeader("Access-Control-Allow-Origin",req.getHeader("origin"));
        req.response().putHeader("Access-Control-Allow-Credentials","true");
        req.response().putHeader("Access-Control-Allow-Methods","GET, POST, PUT, DELETE, OPTIONS");
        req.response().putHeader("Access-Control-Allow-Headers","DNT,X-Mx-ReqToken,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type");
        req.response().putHeader("Access-Control-Max-Age","1728000");
        req.response().putHeader("Content-Type","text/plain charset=UTF-8");
        req.response().setStatusCode(204).end();
        return;
      }
      req.response().putHeader("Access-Control-Allow-Origin",req.getHeader("origin"));
      req.response().putHeader("Access-Control-Allow-Credentials","true");
      switch (req.path()) {
        case "/start": {
          if (!deployId.isEmpty()) {
            req.response().end();
            return;
          }
          String user = req.getParam("user");
          String pass = req.getParam("pass");
          Tray.setCurrent("user",user);
          Tray.setCurrent("pass",pass);
          client = new BaseClient(new UserInfo(user,pass,-1,-1));
          vertx.deployVerticle(client, new DeploymentOptions().setConfig(new JsonObject()
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
        case "/stop":{
          if(!deployId.isEmpty()){
            vertx.undeploy(deployId);
            req.response().end();
          }
        }
        break;
        case "/connect": {
          Tray.setCurrent("token",req.getParam("token"));
          Tray.setCurrent("name",req.getParam("name"));
          Tray.setCurrent("host",req.getParam("host"));
          Tray.setCurrent("port",Integer.parseInt(req.getParam("port")));
          Tray.setCurrent("type",req.getParam("type"));
          JsonObject headers = new JsonObject();
          req.params().entries().forEach(e->{
            if(e.getKey().startsWith("client-"))
            headers.put(e.getKey().substring(7),e.getValue());
          });
          Tray.setCurrent("headers",headers);
          client.reconnect(req.getParam("name")
            ,req.getParam("token")
            ,req.getParam("host")
            ,Integer.parseInt(req.getParam("port"))
            ,req.getParam("type"),headers).setHandler(e->{
            if(e.succeeded()){
              Tray.setStatus(req.getParam("name"));
              req.response().end();
              System.out.println("OK");
            }else{
              Tray.setStatus("连接失败");
              req.response().end(e.cause().getLocalizedMessage());
            }
          });
        }
        break;
        case "/status": {
          if (deployId.isEmpty()) {
            req.response().end("客户端未连接");
          }else {
            req.response().setStatusCode(200).end(client.getStatusMessage());
          }
        }
        break;
        case "/pac":{
          if(req.getParam("host")!=null){
            req.response().end(pac.replace("SOCKS5 127.0.0.1:1080","SOCKS "+req.getParam("host")+":1080"));
          }else {
            req.response().end(pac);
          }
        }
        break;
        case "/global":{
          if(req.getParam("host")!=null){
            req.response().end(global.replace("SOCKS5 127.0.0.1:1080","SOCKS "+req.getParam("host")+":1080"));
          }else {
            req.response().end(global);
          }
        }
        break;
      }
    }).listen(1078, r -> {
      if (r.failed()) r.cause().printStackTrace();
      else System.out.println("Listen at 1078");
    });
    return vertx;
  }
}
