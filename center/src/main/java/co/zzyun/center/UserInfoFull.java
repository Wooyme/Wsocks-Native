package co.zzyun.center;

import co.zzyun.wsocks.data.UserInfo;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

public class UserInfoFull {

  private UserInfo info;
  private AtomicInteger usage = new AtomicInteger(0);
  private HashSet<String> connections = new HashSet<>();
  private String token;
  UserInfoFull(JsonObject json){
    this.info = UserInfo.Companion.fromJson(json);
    if(json.containsKey("usage")) {
      this.usage.set(json.getInteger("usage"));
    }
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }
  public int getUsage() {
    return usage.get();
  }

  public void addUsage(int usage) {
    this.usage.addAndGet(usage);
  }

  public UserInfo getInfo() {
    return info;
  }

  public void setInfo(UserInfo info) {
    this.info = info;
  }

  public void addConnection(String host,String serverName){
    this.connections.add(host+":"+serverName);
  }
  public void removeConnection(String host, String serverName){
    this.connections.remove(host+":"+serverName);
  }
  public int getConnections(){
    return this.connections.size();
  }
  @Override
  public String toString() {
    return new JsonObject()
      .put("user",info.getUsername())
      .put("pass",info.getPassword())
      .put("multiple",info.getMaxLoginDevices())
      .put("usage",usage.get())
      .put("limit",info.getLimitation())
      .toString();
  }

  public JsonObject toJson(){
    return new JsonObject()
      .put("user",info.getUsername())
      .put("pass",info.getPassword())
      .put("multiple",info.getMaxLoginDevices())
      .put("connect",connections.size())
      .put("usage",usage.get())
      .put("limit",info.getLimitation());
  }
}
