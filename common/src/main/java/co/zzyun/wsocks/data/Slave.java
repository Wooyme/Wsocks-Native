package co.zzyun.wsocks.data;

import io.vertx.core.json.JsonObject;

public class Slave {
  private String host;
  private int port;
  private String type;

  public Slave(JsonObject json){
    this.host = json.getString("host");
    this.port = json.getInteger("port");
    this.type = json.getString("type");
  }

  public Slave(String host, int port, String type){
    this.host = host;
    this.port = port;
    this.type = type;
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

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public JsonObject toJson(){
    return new JsonObject().put("host",host).put("port",port).put("type",type);
  }
}
