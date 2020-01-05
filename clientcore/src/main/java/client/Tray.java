package client;

import co.zzyun.wsocks.client.core.Client;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

import java.awt.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Tray {
  private  static SystemTray systemTray = SystemTray.get();
  private static URL LT_GRAY_TRAIN;
  private static JsonObject current = new JsonObject();
  static {
    try {
      LT_GRAY_TRAIN = Tray.class.getResource("/icon/icon.jpg").toURI().toURL();
    } catch (MalformedURLException | URISyntaxException e) {
      e.printStackTrace();
    }
  }

  static void initTray(EventBus eventBus) {
    systemTray.setTooltip("WSocks");
    systemTray.setImage(LT_GRAY_TRAIN);
    systemTray.setStatus("无操作");
    Menu mainMenu = systemTray.getMenu();

    MenuItem openEntry = new MenuItem("打开", event->{
      try {
        Desktop.getDesktop().browse(new URI("http://client.zzyun.co/index.html"));
      } catch (IOException | URISyntaxException e) {
        e.printStackTrace();
      }
    });
    mainMenu.add(openEntry);
    MenuItem reconnect = new MenuItem("重新连接",event->{
      systemTray.setStatus("正在重新连接...");
      eventBus.send("client-connect",current);
      Client.client.reconnect(current.getString("host"),current.getInteger("port"),"websocket");
    });
    mainMenu.add(reconnect);
    MenuItem proxySettingEntry = new MenuItem("PAC代理",(event)->{

    });
    mainMenu.add(proxySettingEntry);
    MenuItem quitEntry = new MenuItem("Quit",event-> System.exit(0));
    mainMenu.add(quitEntry);
  }

  public static void setStatus(String value){
    systemTray.setStatus(value);
  }
  public static void setCurrent(String name,Object value){
    current.put(name,value);
  }
}
