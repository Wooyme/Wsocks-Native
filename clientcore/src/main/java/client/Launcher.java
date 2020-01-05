package client;

import co.zzyun.wsocks.client.core.Client;
import io.vertx.core.Vertx;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class Launcher {
  public static void main(String[] args) throws URISyntaxException, IOException {
    Vertx vertx = Client.start();
    Tray.initTray(vertx.eventBus());
    Desktop.getDesktop().browse(new URI("http://client.zzyun.co/index.html"));
  }
}
