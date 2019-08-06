package co.zzyun.client;

import com.sun.glass.ui.gtk.GtkTray;
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
  public static GtkTray tray;
  private static Process process;
  public static void main(String[] args) {
    String pwd = System.getProperty("user.dir");
    String java_library_path;
    if (args.length >= 1) java_library_path = args[0];
    else java_library_path = "libs";
    System.setProperty("javafx.verbose", "true");
    System.setProperty("prism.verbose", "true");
    System.setProperty("java.library.path", java_library_path);
    Runtime rt = Runtime.getRuntime();
    try {
      process = rt.exec(Paths.get(pwd,"client-core").toString()+" -Xmx128m");
      if(process.isAlive()){
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
    tray = new GtkTray(Paths.get(pwd,"icon.png").toString());
    tray.addMenu("Open", () -> Platform.runLater(primaryStage::show));
    final AtomicBoolean isPac = new AtomicBoolean(true);
    tray.addMenu("PAC mode",()->{
      if(isPac.get()) {
        tray.updateMenu(1, "Global mode");
        isPac.set(false);
      }else{
        tray.updateMenu(1, "Pac mode");
        isPac.set(true);
      }
    });
    tray.addMenu("Exit",()->{
      process.destroyForcibly();
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
    primaryStage.setOnShowing(e->{
      try {
        URL url = new URL("http://localhost:1088/index");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        if (con.getResponseCode() == 200) {
          ((Label) scene.lookup("#statusLabel")).setText("连接成功");
        }else if(con.getResponseCode()==201){
          ((Label) scene.lookup("#statusLabel")).setText("等待..");
        } else {
          ((Label) scene.lookup("#statusLabel")).setText("失败:"+con.getResponseMessage().substring(0,6));
        }
        con.disconnect();
      }catch (IOException err){
        err.printStackTrace();
        ((Label) scene.lookup("#statusLabel")).setText("核心崩溃，请重启本程序");
      }
    });
    Platform.setImplicitExit(false);
    primaryStage.setOnCloseRequest(event -> Platform.runLater(primaryStage::hide));
    primaryStage.show();
  }
}
