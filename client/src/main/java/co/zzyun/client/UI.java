package co.zzyun.client;

import com.sun.glass.ui.Tray;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

public class UI extends Application {
  public static Tray tray;
  private static Process process;

  public static void main(String[] args) {
    String pwd = System.getProperty("user.dir");
    System.setProperty("java.library.path", "libs");
    System.setProperty("prism.order", "sw");
    System.setProperty("prism.text", "t2k");
    System.setProperty("prism.nativepisces", "false");
    System.setProperty("prism.allowhidpi", "false");
    System.setProperty("prism.vsync", "false");
    Runtime rt = Runtime.getRuntime();
    try {
      process = rt.exec(Paths.get(pwd, "client-core").toString() + " -Xmx128m");
      if (process.isAlive()) {
        System.out.println("Client-core startup");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    launch(args);
  }

  @Override
  public void start(Stage primaryStage) {
    String pwd = System.getProperty("user.dir");
    tray = new Tray(Paths.get(pwd, "icon.ico").toString(), false);
    tray.addMenu("Open", () -> Platform.runLater(primaryStage::show));
    final AtomicBoolean isPac = new AtomicBoolean(true);
    tray.addMenu("PAC mode", () -> {
      if (isPac.get()) {
        tray.updateMenu(1, "Global mode");
        isPac.set(false);
      } else {
        tray.updateMenu(1, "Pac mode");
        isPac.set(true);
      }
    });
    tray.addMenu("Exit", () -> {
      try {
        process.destroyForcibly();
      } catch (Throwable e) {
      }
      System.exit(0);
    });
    Parent root;
    try {
      root = FXMLLoader.load(new File("ui.fxml").toURI().toURL());
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
    primaryStage.setTitle("WSocks");
    Scene scene = new Scene(root, 660, 295);
    primaryStage.setScene(scene);
    primaryStage.setOnShowing(e -> {
      try {
        URL url = new URL("http://127.0.0.1:1088/index");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        if (con.getResponseCode() == 200) {
          ((Label) scene.lookup("#statusLabel")).setText("连接成功");
        } else if (con.getResponseCode() == 201) {
          ((Label) scene.lookup("#statusLabel")).setText("等待..");
        } else {
          ((Label) scene.lookup("#statusLabel")).setText("失败:" + con.getResponseMessage().substring(0, 6));
        }
        con.disconnect();
      } catch (IOException err) {
        err.printStackTrace();
        ((Label) scene.lookup("#statusLabel")).setText("核心启动失败，请重启本程序");
      }
    });
    Platform.setImplicitExit(false);
    primaryStage.setOnCloseRequest(event -> Platform.runLater(primaryStage::hide));
    System.out.println(System.getProperty("java.awt.graphicsenv"));
    primaryStage.show();
  }
}
