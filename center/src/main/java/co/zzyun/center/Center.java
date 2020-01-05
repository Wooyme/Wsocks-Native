package co.zzyun.center;

import co.zzyun.wsocks.data.Slave;
import co.zzyun.wsocks.data.UserInfo;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Center {
  private static Map<String, UserInfoFull> userMap = new ConcurrentHashMap<>(); // username, UserInfoFull
  private static Map<String, UserInfoFull> tokenMap = new ConcurrentHashMap<>(); // token, UserInfoFull
  private static Set<Slave> hosts = new ConcurrentHashSet<>();
  private static String userPath = "users";

  /*args: [0]-udp端口 [1]-http端口 [2]-god秘钥 [3]-slave秘钥 [4]-salt*/
  public static void main(String[] args) {
    System.setProperty("java.net.preferIPv4Stack", "true");
    System.setProperty("io.netty.noUnsafe", "true");
    final SimpleUdp server = new SimpleUdp(Integer.valueOf(args[0]));
    server.handler(p -> {
      String host = p.getAddress().getHostAddress();
      int port = p.getPort();
      server.send(port, p.getAddress(), new JsonObject().put("port",port).put("host",host)
        .toBuffer());
    });
    server.start();
    Vertx vertx = Vertx.vertx(
      new VertxOptions().setFileSystemOptions(new FileSystemOptions()
        .setFileCachingEnabled(false)
        .setClassPathResolvingEnabled(false))
        .setWorkerPoolSize(1).setEventLoopPoolSize(1).setInternalBlockingPoolSize(1));
    vertx.setPeriodic(60*1000,id-> {
      List<String> list = new LinkedList<>();
      tokenMap.forEach((k, v)->{
        if(v.getConnections()==0){
          list.add(k);
        }
      });
      list.forEach(v->tokenMap.remove(v));
      }
    );
    vertx.createHttpServer().requestHandler(req -> {
      System.out.println("[Req]:" + req.path()+"?" + req.query());
      switch (req.path()) {
        // login?user=&pass=
        case "/login":{
          UserInfoFull user = userMap.get(req.getParam("user"));
          if (user == null) {
            try {
              user = new UserInfoFull(new JsonObject(new Scanner(Paths.get(userPath, req.getParam("user")).toFile()).useDelimiter("\\Z").next()));
              userMap.put(user.getInfo().getUsername(), user);
            } catch (FileNotFoundException e) {
              req.response().setStatusCode(500).setStatusMessage("No such user").end();
              return;
            }
          }
          if (!user.getInfo().getPassword().equals(req.getParam("pass"))) {
            req.response().setStatusCode(501).setStatusMessage("Password wrong").end();
            return;
          }
          if(user.getToken()==null){
            String token = RandomStringUtils.randomAlphanumeric(16);
            tokenMap.put(token, user);
            user.setToken(token);
          }
          req.response().putHeader("x-token",user.getToken()).putHeader("x-salt",args[4]);
          int i =0;
          for (Slave host : hosts) {
            req.response().putHeader("x-host" + i, host.toString());
            i++;
          }
          System.out.println("Client["+user.getToken()+"] login");
          req.response().end();
        }
        break;
        // hosts?god=
        case "/hosts": {
          if (args[2].equals(req.getParam("god"))) {
            int i =0;
            for (Slave host : hosts) {
              String hostStr = host.getHost() + "+" + host.getPort();
              req.response().putHeader("x-host" + i, hostStr);
              i++;
            }
            req.response().end();
            return;
          }else{
            req.response().setStatusCode(500).end();
          }
        }
        break;
        // slave?slave=&name=&port=
        case "/slave": {
          if (!req.getParam("slave").equals(args[3])) {
            req.response().end();
          } else {
            hosts.add(new Slave(req.remoteAddress().host(), Integer.valueOf(req.getParam("port")),req.getParam("name")));
            req.response().end();
          }
        }
        break;
        // online?token=&host=&name=
        case "/online": {
          System.out.println("[Online]:"+req.getParam("token"));
          if (tokenMap.containsKey(req.getParam("token"))) {
            UserInfoFull uInfo = tokenMap.get(req.getParam("token"));
            uInfo.addConnection(req.getParam("host"),req.getParam("name"));
            req.response()
              .putHeader("x-key", Base64.encodeBase64String(uInfo.getInfo().getKey()).replace("\r","").replace("\n",""))
              .end();
          } else {
            req.response().setStatusCode(500).end();
          }
        }
        break;
        // offline?token=
        case "/offline": {
          String token = req.getParam("token");
          if(tokenMap.containsKey(token)) {
            tokenMap.get(token).removeConnection(req.getParam("host"), req.getParam("name"));
            System.out.println("["+req.getParam("host")+"]:与 "+req.getParam("name")+" 断开连接");
          }else{
            System.out.println("["+req.getParam("host")+"] 未登录");
          }
          req.response().end();
        }
        break;
        // update?token=&usage=
        case "/update": {
          String token = req.getParam("token");
          int usage = Integer.valueOf(req.getParam("usage"));
          UserInfoFull user = tokenMap.get(token);
          if (user != null) {
            user.addUsage(usage);
            try {
              PrintWriter out = new PrintWriter(Paths.get(userPath, user.getInfo().getUsername()).toString());
              out.println(user.toString());
              out.close();
            } catch (FileNotFoundException e) {
              e.printStackTrace();
            }
          }
          req.response().end();
        }
        break;
        case "/status": {
          if (!req.getParam("god").equals(args[2])) {
            req.response().setStatusCode(500).end();
            return;
          }
          JsonArray array = new JsonArray();
          userMap.forEach((k, v) -> array.add(v.toJson()));
          req.response().end(array.toBuffer());
        }
        break;
        case "/user": {
          if (!req.getParam("god").equals(args[2])) {
            req.response().setStatusCode(500).end();
            return;
          }
          try {
            editUser(req.getParam("user"), req.getParam("pass"), req.getParam("max"), req.getParam("limit"));
            req.response().end();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        break;
        default:
        {
          req.response().setStatusCode(404).setStatusMessage("Not found").end();
        }
        break;
      }
    }).listen(Integer.valueOf(args[1]), l -> System.out.println("Server listen at " + args[1]));
  }

  private static void editUser(String username, String password, String maxLoginDevices, String limitation) throws IOException {
    UserInfoFull user = userMap.get(username);
    if (user == null) {
      JsonObject json = new JsonObject();
      json.put("user", username)
        .put("pass", password)
        .put("multiple", maxLoginDevices != null ? Integer.valueOf(maxLoginDevices) : -1)
        .put("limit", limitation != null ? Integer.valueOf(limitation) : -1);
      user = new UserInfoFull(json);
      userMap.put(user.getInfo().getUsername(), user);
      File userFile = Paths.get(userPath, username).toFile();
      if (!userFile.exists())
        Paths.get(userPath, username).toFile().createNewFile();
      PrintWriter out = new PrintWriter(Paths.get(userPath, username).toString());
      out.println(user.toString());
      out.close();
    } else {
      if (password == null) {
        userMap.remove(user.getInfo().getUsername());
        Paths.get(userPath, username).toFile().delete();
      } else {
        JsonObject json = new JsonObject();
        json.put("user", username)
          .put("pass", password)
          .put("multiple", maxLoginDevices != null ? Integer.valueOf(maxLoginDevices) : -1)
          .put("limit", limitation != null ? Integer.valueOf(limitation) : -1);
        user.setInfo(UserInfo.Companion.fromJson(json));
        PrintWriter out = new PrintWriter(Paths.get(userPath, username).toString());
        out.println(user.toString());
        out.close();
      }
    }
  }
}
