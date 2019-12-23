package co.zzyun.client;

import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;

import java.awt.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

class Tray {
  private  static SystemTray systemTray = SystemTray.get();
  private static URL LT_GRAY_TRAIN;
  static {
    try {
      LT_GRAY_TRAIN = Tray.class.getResource("/icon/icon.jpg").toURI().toURL();
    } catch (MalformedURLException | URISyntaxException e) {
      e.printStackTrace();
    }
  }

  static void initTray() {
    systemTray.setTooltip("WSocks");
    systemTray.setImage(LT_GRAY_TRAIN);
    systemTray.setStatus("No Action");
    Menu mainMenu = systemTray.getMenu();
    MenuItem openEntry = new MenuItem("打开",event->{
      try {
        Desktop.getDesktop().browse(new URI("http://localhost:1078"));
      } catch (IOException | URISyntaxException e) {
        e.printStackTrace();
      }
    });
    mainMenu.add(openEntry);
    MenuItem proxySettingEntry = new MenuItem("PAC代理",(event)->{

    });
    mainMenu.add(proxySettingEntry);
    MenuItem quitEntry = new MenuItem("Quit",event-> System.exit(0));
    mainMenu.add(quitEntry);
  }
}
