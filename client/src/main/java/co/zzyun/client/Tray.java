package co.zzyun.client;


import java.awt.*;
import java.net.URL;

class Tray {
  private static URL LT_GRAY_TRAIN = Tray.class.getResource("/icon/icon.jpg");
  static void initTray(){
    if (!SystemTray.isSupported()) {
      System.out.println("SystemTray is not supported");
      return;
    }
    final PopupMenu popup = new PopupMenu();
    URL url = LT_GRAY_TRAIN;
    Image image = Toolkit.getDefaultToolkit().getImage(url);
    final TrayIcon trayIcon = new TrayIcon(image);
    final SystemTray tray = SystemTray.getSystemTray();
    Menu displayMenu = new Menu("Display");
    MenuItem infoItem = new MenuItem("Info");
    MenuItem exitItem = new MenuItem("Exit");
    exitItem.addActionListener(e-> System.exit(0));
    popup.add(displayMenu);
    displayMenu.add(infoItem);
    popup.add(exitItem);

    trayIcon.setPopupMenu(popup);

    try {
      tray.add(trayIcon);
    } catch (AWTException e) {
      System.out.println("TrayIcon could not be added.");
    }
  }
}
