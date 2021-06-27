package co.zzyun.wsocks.client.core;

import client.Tray;
import co.zzyun.wsocks.Settings;
import co.zzyun.wsocks.client.core.client.BaseClient;
import co.zzyun.wsocks.data.UserInfo;
import co.zzyun.wsocks.tester.MemcachedTester;
import co.zzyun.wsocks.tester.RedisTester;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;


public class Client {
  private static String deployId = "";
  public static BaseClient client;
  private static MemcachedTester memcachedTester;
  private static RedisTester redisTester;
  private static void initTester(Vertx vertx){
    memcachedTester = new MemcachedTester(vertx);
    redisTester = new RedisTester(vertx);
  }
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
    initTester(vertx);
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
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.route().handler(ctx->{
      HttpServerRequest req = ctx.request();
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
      ctx.next();
    });
    router.route("/start").handler(ctx->{
      HttpServerRequest req = ctx.request();
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
    });
    router.route("/stop").handler(ctx->{
      if(!deployId.isEmpty()){
        vertx.undeploy(deployId);
        ctx.response().end();
      }
    });
    router.route("/connect").handler(ctx->{
      HttpServerRequest req = ctx.request();
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
        ,req.getParam("type"),headers,0).setHandler(e->{
        if(e.succeeded()){
          Tray.setStatus(req.getParam("name"));
          req.response().end();
          System.out.println("OK");
        }else{
          Tray.setStatus("连接失败");
          req.response().end(e.cause().getLocalizedMessage());
        }
      });
    });
    router.route("/status").handler(ctx->{
      HttpServerRequest req = ctx.request();
      if (deployId.isEmpty()) {
        req.response().end("客户端未连接");
      }else {
        req.response().setStatusCode(200).end(client.getStatusMessage());
      }
    });
    router.route("/pac").handler(ctx->{
      HttpServerRequest req = ctx.request();
      if(req.getParam("host")!=null){
        req.response().end(pac.replace("SOCKS5 127.0.0.1:1080","SOCKS "+req.getParam("host")+":1080"));
      }else {
        req.response().end(pac);
      }
    });
    router.route("/global").handler(ctx->{
      HttpServerRequest req = ctx.request();
      if(req.getParam("host")!=null){
        req.response().end(global.replace("SOCKS5 127.0.0.1:1080","SOCKS "+req.getParam("host")+":1080"));
      }else {
        req.response().end(global);
      }
    });
    router.route("/test/memcached").handler(ctx->{
      JsonArray array = ctx.getBodyAsJsonArray();
      System.out.println(array);
      ((Future<String>)memcachedTester.test(array.getList())).setHandler(res->{
        if(res.failed()){
          ctx.response().end(new JsonObject().put("status",-1).toBuffer());
        }else{
          ctx.response().end(new JsonObject().put("status",1).put("ip",res.result()).toBuffer());
        }
      });
    });

    router.route("/test/redis").handler(ctx->{
      JsonArray array = ctx.getBodyAsJsonArray();
      System.out.println(array);
      ((Future<String>)redisTester.test(array.getList())).setHandler(res->{
        if(res.failed()){
          ctx.response().end(new JsonObject().put("status",-1).toBuffer());
        }else{
          ctx.response().end(new JsonObject().put("status",1).put("ip",res.result()).toBuffer());
        }
      });
    });

    router.route("/settings").handler(ctx->{
      JsonObject obj = ctx.getBodyAsJson();
      System.out.println(obj);
      if(obj.containsKey("connection_default")){
        Settings.INSTANCE.setCONNECTION_DEFAULT(obj.getInteger("connection_default"));
      }
      if(obj.containsKey("connection_mag")){
        Settings.INSTANCE.setCONNECTION_MAG(obj.getInteger("connection_mag"));
      }
      if(obj.containsKey("connection_max")){
        Settings.INSTANCE.setCONNECTION_MAX(obj.getInteger("connection_max"));
      }
      if(obj.containsKey("connection_delay")){
        Settings.INSTANCE.setCONNECTION_DELAY(obj.getInteger("connection_delay"));
      }
      if(obj.containsKey("connection_lag_limit")){
        Settings.INSTANCE.setCONNECTION_LAG_LIMIT(obj.getInteger("connection_lag_limit"));
      }
      ctx.response().end(new JsonObject().put("status",1).toBuffer());
    });

    vertx.createHttpServer().requestHandler(router).listen(1078, r -> {
      if (r.failed()) r.cause().printStackTrace();
      else System.out.println("Listen at 1078");
    });
    return vertx;
  }
}
