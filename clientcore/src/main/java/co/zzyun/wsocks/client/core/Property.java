package co.zzyun.wsocks.client.core;

import io.vertx.core.json.JsonObject;

public class Property {
  private String host;
  private int port;
  private String username;
  private String password;

  public Property(String host, int port, String username, String password){
    this.host = host;
    this.port = port;
    this.username = username;
    this.password = password;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  @Override
  public String toString(){
    return this.host+":"+this.port;
  }

  public JsonObject toJson(){
    return new JsonObject().put("host",this.host)
      .put("port",this.port)
      .put("user",this.username)
      .put("pass",this.password);
  }

  public static Property fromJson(JsonObject json){
    return new Property(json.getString("host")
      ,json.getInteger("port")
      ,json.getString("user")
      ,json.getString("pass"));
  }
}
