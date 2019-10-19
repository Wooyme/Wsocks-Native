package co.zzyun.center;

import co.zzyun.wsocks.data.UserInfo;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UserInfoFull {
  private UserInfo info;
  private AtomicInteger usage = new AtomicInteger(0);
  private HashMap<String,ConnectInfo> tokens = new HashMap<>();
  UserInfoFull(JsonObject json){
    this.info = UserInfo.Companion.fromJson(json);
    if(json.containsKey("usage")) {
      this.usage.set(json.getInteger("usage"));
    }
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

  public HashMap<String,ConnectInfo> getTokens() {
    return tokens;
  }


  @Override
  public String toString() {
    return new JsonObject()
      .put("user",info.getUsername())
      .put("pass",info.getPassword())
      .put("multiple",info.getMaxLoginDevices())
      .put("connect",tokens.size())
      .put("usage",usage.get())
      .put("limit",info.getLimitation())
      .toString();
  }

  public JsonObject toJson(){
    return new JsonObject()
      .put("user",info.getUsername())
      .put("pass",info.getPassword())
      .put("multiple",info.getMaxLoginDevices())
      .put("connect",tokens.size())
      .put("usage",usage.get())
      .put("limit",info.getLimitation());
  }
}
