package co.zzyun.client;

import com.sun.glass.ui.gtk.GtkTray;
import io.vertx.core.Vertx;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

public class UI extends Application {
  public static Vertx vertx;
  public static GtkTray tray;
  public static void main(String[] args) {
    String java_library_path;
    if (args.length >= 1) java_library_path = args[0];
    else java_library_path = "libs";
    System.setProperty("javafx.verbose", "true");
    System.setProperty("prism.verbose", "true");
    System.setProperty("java.library.path", java_library_path);
    launch(args);
  }

  @Override
  public void start(Stage primaryStage) {
    String pwd = System.getProperty("user.dir");
    tray = new GtkTray(Paths.get(pwd,"icon.png").toString());
    tray.addMenu("Waiting",null);
    tray.addMenu("Edit", () -> Platform.runLater(primaryStage::show));
    final AtomicBoolean isPac = new AtomicBoolean(true);
    tray.addMenu("PAC mode",()->{
      if(isPac.get()) {
        tray.updateMenu(2, "Global mode");
        isPac.set(false);
      }else{
        tray.updateMenu(2, "Pac mode");
        isPac.set(true);
      }
    });
    tray.addMenu("Exit",()->System.exit(0));
    Parent root;
    try {
      root = FXMLLoader.load(new File("ui.fxml").toURI().toURL());
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
    primaryStage.setTitle("WSocks");
    primaryStage.setScene(new Scene(root, 660, 295));
    Platform.setImplicitExit(false);
    primaryStage.setOnCloseRequest(event -> Platform.runLater(primaryStage::hide));
    primaryStage.show();
  }
}
