package co.zzyun.client;

public class Property{
  private String name;
  private String host;
  private int port;

  public Property(String host,int port,String name){
    this.host = host;
    this.port = port;
    this.name = name;
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
  @Override
  public String toString(){
    return this.name+":"+this.host;
  }

  public String toLocalString(){
    return this.host+"+"+this.port+"+"+this.name;
  }

  public static Property fromLocalString(String line){
    int hostEnd = line.indexOf("+");
    int portEnd = line.indexOf("+",hostEnd+1);
    String host = line.substring(0,hostEnd);
    int port = Integer.valueOf(line.substring(hostEnd+1,portEnd));
    String name = line.substring(portEnd+1);
    return new Property(host,port,name);
  }


}
