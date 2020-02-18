package client;

import client.browser.UserAgent;
import client.browser.WebEngineUtils;
import co.zzyun.wsocks.client.core.Client;
import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Launcher extends Application {
  private static String USERAGENT = UserAgent.randomUserAgent();
  private static String url;

  public static void main(String[] args) {
    if (args.length == 0) {
      Client.start();
      Tray.initTray();
      openWindow("http://www.zzyun.co/client/desktop/index.html");
    } else {
      System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
      System.out.println(args[0]);
      url = args[0];
      launch(args);
    }
  }

  @Override
  public void start(Stage primaryStage) {
    primaryStage.setTitle("Super Engine");
    WebView webView = new WebView();
    final WebEngine engine = webView.getEngine();
    engine.load(url);
    engine.setUserAgent(USERAGENT);
    engine.setUserStyleSheetLocation("data:,body { font: 15px 'DejaVu Sans Mono';}");
    WebEngineUtils utils = new WebEngineUtils(engine);
    engine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
      if(newValue== Worker.State.SUCCEEDED) {
        JSObject window = (JSObject) engine.executeScript("window");
        window.setMember("engine", utils);
        System.out.println("Inject engine");
        window.call("onInjected");
      }
    });
    VBox vBox = new VBox(webView);
    Scene scene = new Scene(vBox, 1200, 600);
    primaryStage.setScene(scene);
    primaryStage.setOnCloseRequest(ev -> utils.allClosed.set(true));
    primaryStage.show();
  }


  public static void openWindow(String url) {
    if (System.getProperty("java.launcher.path") == null) {
      startUp(new String[]{url});
    } else {
      String path = System.getProperty("java.launcher.path");
      String separator = System.getProperty("file.separator");
      ArrayList<String> launchArgs = new ArrayList<>();
      if (System.getProperty("os.name").toLowerCase().contains("win")) {
        launchArgs.add(path + separator + "Wsocks.exe");
      } else {
        launchArgs.add(path + separator + "Wsocks");
      }
      launchArgs.add(url);
      try {
        ProcessBuilder processBuilder = new ProcessBuilder(launchArgs);
        processBuilder.start();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static void startUp(String[] args) {
    String separator = System.getProperty("file.separator");
    String classpath = System.getProperty("java.class.path");
    String mainClass = "client.Launcher";
    String jvmPath = System.getProperty("java.home") + separator + "bin" + separator + "java";

    List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();

    ArrayList<String> jvmArgs = new ArrayList<>();

    jvmArgs.add(jvmPath);
    jvmArgs.addAll(inputArguments);
    jvmArgs.add("-cp");
    jvmArgs.add(classpath);
    jvmArgs.add(mainClass);
    Collections.addAll(jvmArgs, args);

    try {
      ProcessBuilder processBuilder = new ProcessBuilder(jvmArgs);
      processBuilder.start();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }
}
