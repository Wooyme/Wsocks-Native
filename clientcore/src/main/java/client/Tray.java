package client;

import co.zzyun.wsocks.client.core.Client;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import io.vertx.core.json.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Base64;

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

  static void initTray() {
    systemTray.setTooltip("WSocks");
    systemTray.setImage(LT_GRAY_TRAIN);
    systemTray.setStatus("无操作");
    Menu mainMenu = systemTray.getMenu();

    MenuItem openEntry = new MenuItem("打开", event->{
      try {
        String user = current.getString("user");
        String pass = current.getString("pass");
        if(user!=null && pass!=null){
          String encode = Base64.getEncoder().encodeToString(new JsonObject().put("user",user).put("pass",pass).toString().getBytes());
          Desktop.getDesktop().browse(new URI("http://www.zzyun.co/client/index.html?code="+encode));
        }else
          Desktop.getDesktop().browse(new URI("http://www.zzyun.co/client/index.html"));
      } catch (IOException | URISyntaxException e) {
        e.printStackTrace();
      }
    });
    mainMenu.add(openEntry);
    MenuItem reconnect = new MenuItem("重新连接",event->{
      systemTray.setStatus("正在重新连接...");
      Client.client.reconnect(current.getString("token"),current.getString("host"),current.getInteger("port"),"websocket").setHandler((e)->{
        if(e.succeeded()){
          systemTray.setStatus(current.getString("name"));
        }else{
          systemTray.setStatus("连接失败");
        }
      });
    });
    mainMenu.add(reconnect);
    final MenuItem proxySettingEntry = new MenuItem("PAC代理");
    proxySettingEntry.setCallback((event)->{
      if(System.getProperty("os.name").startsWith("Windows")){
        if(proxySettingEntry.getText().equals("PAC代理")){
          try {
            WinRegistry.writeStringValue(WinRegistry.HKEY_CURRENT_USER
              ,"Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
              ,"AutoConfigURL","http://localhost:1078/global");
          } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,"设置全局代理失败,"+e.getLocalizedMessage());
          }
          proxySettingEntry.setText("全局代理");
        }else{
          try {
            WinRegistry.writeStringValue(WinRegistry.HKEY_CURRENT_USER
              ,"Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
              ,"AutoConfigURL","http://localhost:1078/pac");
          } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,"设置PAC失败,"+e.getLocalizedMessage());
          }
          proxySettingEntry.setText("PAC代理");
        }
      }else{
        JOptionPane.showMessageDialog(null,"Windows Only");
      }

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
