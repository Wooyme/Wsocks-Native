package co.zzyun.center;

public class ConnectInfo {
  private String ip;
  private int port;

  ConnectInfo(String ip,int port){
    this.ip = ip;
    this.port = port;
  }

  public int getPort() {
    return port;
  }

  public String getIp() {
    return ip;
  }
}
