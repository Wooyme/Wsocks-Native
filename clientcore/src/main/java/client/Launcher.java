package client;

import co.zzyun.wsocks.client.core.Client;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class Launcher {
  public static void main(String[] args) throws URISyntaxException, IOException {
    Client.start();
    Tray.initTray();
    Desktop.getDesktop().browse(new URI("http://www.zzyun.co/client/index.html"));
  }
}
