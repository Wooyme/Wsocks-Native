package co.zzyun.center;

import co.zzyun.wsocks.data.Slave;
import co.zzyun.wsocks.data.UserInfo;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystemOptions;
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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Scanner;

public class Center {
  private static HashMap<String, UserInfoFull> userMap = new HashMap<>(); // username, UserInfoFull
  private static HashMap<String, UserInfoFull> tokenMap = new HashMap<>(); // token, UserInfoFull
  private static LinkedList<Slave> hosts = new LinkedList<>();
  private static String userPath = "users";

  /*args: [0]-udp端口 [1]-http端口 [2]-god秘钥 [3]-slave秘钥 [4]-salt*/
  public static void main(String[] args) {
    System.setProperty("java.net.preferIPv4Stack", "true");
    System.setProperty("io.netty.noUnsafe", "true");
    final SimpleUdp server = new SimpleUdp(Integer.valueOf(args[0]));
    server.handler(p -> {
      String host = p.getAddress().getHostAddress();
      int port = p.getPort();
      JsonObject json = Buffer.buffer(p.getData()).toJsonObject();
      UserInfoFull user = userMap.get(json.getString("user"));
      if (user == null) {
        try {
          user = new UserInfoFull(new JsonObject(new Scanner(Paths.get(userPath, json.getString("user")).toFile()).useDelimiter("\\Z").next()));
          userMap.put(user.getInfo().getUsername(), user);
        } catch (FileNotFoundException e) {
          server.send(port, p.getAddress(), new JsonObject().put("status", -1).toBuffer());
          return;
        }
      }
      if (!user.getInfo().getPassword().equals(json.getString("pass"))) {
        server.send(port, p.getAddress(), new JsonObject().put("status", -1).toBuffer());
        return;
      }
      if (user.getInfo().getMaxLoginDevices() != -1 && user.getTokens().size() > user.getInfo().getMaxLoginDevices()) {
        server.send(port, p.getAddress(), new JsonObject().put("status", -2).toBuffer());
        return;
      }
      String token = RandomStringUtils.randomAlphanumeric(16);
      tokenMap.put(token, user);
      user.getTokens().put(token, new ConnectInfo(host, port));
      server.send(port, p.getAddress(), new JsonObject()
        .put("status", 1)
        .put("token", token)
        .toBuffer());
    });
    server.start();
    Vertx vertx = Vertx.vertx(
      new VertxOptions().setFileSystemOptions(new FileSystemOptions()
        .setFileCachingEnabled(false)
        .setClassPathResolvingEnabled(false))
        .setWorkerPoolSize(1).setEventLoopPoolSize(1).setInternalBlockingPoolSize(1));
    vertx.createHttpServer().requestHandler(req -> {
      System.out.println("[Req]:" + req.path() + req.query());
      switch (req.path()) {
        case "/hosts": {
          if (args[2].equals(req.getParam("god"))) {
            for (int i = 0; i < hosts.size(); i++) {
              String host = hosts.get(i).getHost() + "+" + hosts.get(i).getPort() + "+" + hosts.get(i).getType();
              req.response().putHeader("x-host" + i, host);
            }
            req.response().end();
            return;
          }
          String timestamp = req.getParam("t");
          String version = req.getParam("v");
          String secret = req.getParam("s");
          String salt = args[4];
          if (new Date().getTime() - Long.valueOf(timestamp) > 10 * 1000) {
            req.response().setStatusCode(500).end();
            return;
          }
          if (secret.equals(DigestUtils.md5Hex(timestamp + version + salt))) {
            for (int i = 0; i < hosts.size(); i++) {
              String host = hosts.get(i).getHost() + "+" + hosts.get(i).getPort() + "+" + hosts.get(i).getType();
              req.response().putHeader("x-host" + i, host);
            }
            req.response().end();
            return;
          }
        }
        break;
        case "/slave": {
          if (!req.getParam("slave").equals(args[3])) {
            req.response().end();
          } else {
            String type = req.getParam("type");
            for (Slave host : hosts) {
              if (host.getType().equals(type)) {
                host.setHost(req.remoteAddress().host());
                host.setPort(Integer.valueOf(req.getParam("port")));
                req.response().end();
                return;
              }
            }
            hosts.add(new Slave(req.remoteAddress().host(), Integer.valueOf(req.getParam("port")), req.getParam("type")));
            req.response().end();
          }
        }
        break;
        case "/online": {
          if (tokenMap.containsKey(req.getParam("token"))) {
            UserInfo uInfo = tokenMap.get(req.getParam("token")).getInfo();
            ConnectInfo cInfo = tokenMap.get(req.getParam("token")).getTokens().get(req.getParam("token"));
            req.response().putHeader("x-ip", cInfo.getIp())
              .putHeader("x-port", String.valueOf(cInfo.getPort()))
              .putHeader("x-key", Base64.encodeBase64String(uInfo.getKey()).replace("\r","").replace("\n",""))
              .end();
          } else {
            req.response().setStatusCode(500).end();
          }
        }
        break;
        case "/offline": {
          String token = req.getParam("token");
          tokenMap.remove(token).getTokens().remove(token);
          req.response().end();
        }
        break;
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
