package co.zzyun.center;

import io.vertx.core.json.JsonObject;

public class Center {
  public static void main(String[] args) {
    System.setProperty("java.net.preferIPv4Stack","true");
    System.setProperty("io.netty.noUnsafe","true");
    final SimpleUdp server = new SimpleUdp(7091);
    server.handler(p->{
      String host = p.getAddress().getHostAddress();
      int port = p.getPort();
      server.send(port,p.getAddress(),new JsonObject().put("port",port).put("host",host).toBuffer());
    });
    server.run();
  }
}
