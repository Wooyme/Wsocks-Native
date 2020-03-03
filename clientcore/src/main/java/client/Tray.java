package client;

import co.zzyun.wsocks.client.core.Client;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import io.vertx.core.json.JsonObject;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Base64;

public class Tray {
  private static SystemTray systemTray = SystemTray.get();
  private static URL LT_GRAY_TRAIN;
  private static JsonObject current = new JsonObject();

  static {
    try {
      LT_GRAY_TRAIN = Tray.class.getResource("/icon/icon.jpg").toURI().toURL();
    } catch (MalformedURLException | URISyntaxException e) {
      e.printStackTrace();
    }
  }

  static void initTray() {
    if (System.getProperty("os.name").startsWith("Windows")) {
      try {
        WinRegistry.writeStringValue(WinRegistry.HKEY_CURRENT_USER
          , "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
          , "AutoConfigURL", "http://localhost:1078/pac");
      } catch (IllegalAccessException | InvocationTargetException e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(null, "设置PAC代理失败," + e.getLocalizedMessage());
      }
    }
    systemTray.setTooltip("WSocks");
    systemTray.setImage(LT_GRAY_TRAIN);
    systemTray.setStatus("无操作");

    Menu mainMenu = systemTray.getMenu();

    MenuItem openEntry = new MenuItem("打开", event -> {
      String user = current.getString("user");
      String pass = current.getString("pass");
      if (user != null && pass != null) {
        String encode = Base64.getEncoder().encodeToString(new JsonObject().put("user", user).put("pass", pass).toString().getBytes());
        Launcher.openWindow("http://www.zzyun.co/client/desktop/index.html?code="+encode);
      } else
        Launcher.openWindow("http://www.zzyun.co/client/desktop/index.html");
    });
    mainMenu.add(openEntry);
    MenuItem reconnect = new MenuItem("重新连接", event -> {
      systemTray.setStatus("正在重新连接...");
      Client.client.reconnect(current.getString("name")
        ,current.getString("token")
        , current.getString("host")
        , current.getInteger("port")
        , current.getString("type")
        , current.getJsonObject("headers")).setHandler((e) -> {
        if (e.succeeded()) {
          systemTray.setStatus(current.getString("name"));
        } else {
          systemTray.setStatus("连接失败");
        }
      });
    });
    mainMenu.add(reconnect);
    final MenuItem proxySettingEntry = new MenuItem("PAC代理");
    proxySettingEntry.setCallback((event) -> {
      if (System.getProperty("os.name").startsWith("Windows")) {
        if (proxySettingEntry.getText().equals("PAC代理")) {
          try {
            WinRegistry.writeStringValue(WinRegistry.HKEY_CURRENT_USER
              , "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
              , "AutoConfigURL", "http://localhost:1078/global");
            JOptionPane.showMessageDialog(null, "全局代理设置成功");
          } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "设置全局代理失败," + e.getLocalizedMessage());
          }
          proxySettingEntry.setText("全局代理");
        } else {
          try {
            WinRegistry.writeStringValue(WinRegistry.HKEY_CURRENT_USER
              , "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
              , "AutoConfigURL", "http://localhost:1078/pac");
            JOptionPane.showMessageDialog(null, "PAC代理设置成功");
          } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "设置PAC失败," + e.getLocalizedMessage());
          }
          proxySettingEntry.setText("PAC代理");
        }
      } else {
        JOptionPane.showMessageDialog(null, "Windows Only");
      }
    });
    mainMenu.add(proxySettingEntry);
    final MenuItem getProxyURL = new MenuItem("代理链接",event->{
      try {
        String localHost = IPUtils.getFirstNonLoopbackAddress(true,false).getHostAddress();
        JOptionPane.showInputDialog(null,"代理链接","http://"+localHost+":1078/pac?host="+localHost);
      } catch (Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(null,"获取代理链接失败");
      }
    });
    mainMenu.add(getProxyURL);
    MenuItem quitEntry = new MenuItem("退出", event -> {
      if (System.getProperty("os.name").startsWith("Windows")) {
        try {
          WinRegistry.deleteValue(WinRegistry.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings", "AutoConfigURL");
        } catch (IllegalAccessException | InvocationTargetException e) {
          e.printStackTrace();
          JOptionPane.showMessageDialog(null, "清除代理设置失败" + e.getLocalizedMessage());
        }
      }
      System.exit(0);
    });
    mainMenu.add(quitEntry);
  }

  public static void setStatus(String value) {
    systemTray.setStatus(value);
  }

  public static void setCurrent(String name, Object value) {
    current.put(name, value);
  }
}
