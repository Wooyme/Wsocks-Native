package client;

import co.zzyun.wsocks.client.core.Client;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.sun.webkit.network.CookieManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.html.HTMLIFrameElement;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.CookieHandler;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Launcher extends Application {
  private static String url;

  public static void main(String[] args) {
    if (args.length == 0) {
      Client.start();
      Tray.initTray();
      try {
        String code = FileUtils.readFileToString(Paths.get(System.getProperty("user.home"),".wsocks","local.txt").toFile());
        openWindow("http://www.zzyun.co/client/v2/index.html?code="+code);
      } catch (IOException e) {
        openWindow("http://www.zzyun.co/client/v2/index.html");
      }
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
    engine.setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.100 Safari/537.36");
    engine.setUserStyleSheetLocation("data:,body { font: 15px 'DejaVu Sans Mono';}");
    AtomicBoolean canClose = new AtomicBoolean(false);
    engine.setOnAlert(event -> {
      if(event.getData().startsWith("CODE:")){
        try {
          FileUtils.writeStringToFile(Paths.get(System.getProperty("user.home"),".wsocks","local.txt").toFile(),event.getData().replace("CODE:",""));
        } catch (IOException e) {
          e.printStackTrace();
        }
        return;
      }
      if(event.getData().equals("loading")){
        Platform.setImplicitExit(false);
        return;
      }
      Document doc = engine.getDocument();
      HTMLIFrameElement iframeElement = (HTMLIFrameElement) doc.getElementById("ads-iframe");
      String currentUrl = iframeElement.getAttribute("src");
      Document iframeContentDoc = iframeElement.getContentDocument();
      Element rootElement = iframeContentDoc.getDocumentElement();
      int clickX = new Random().nextInt(120) + 19;
      int clickY = new Random().nextInt(100) + 21;
      String url = rootElement.getElementsByTagName("a").item(0).toString() + "&clickX=" + clickX + "&clickY=" + clickY;
      new Thread(()->{
        try {
          String html = gotoAds(url, getCookie(), currentUrl);
          Matcher m = Pattern.compile("url=(\\S+)\"").matcher(html);
          if (m.find()) {
            String href = m.group(1);
            System.out.println(href);
            gotoAds(href,null,null);
          }
        } catch (Exception ignored) {
        } finally {
          System.out.println("Ads finished");
          Platform.setImplicitExit(true);
          if(canClose.get()){
            System.exit(0);
          }
        }
      }).start();
    });
    VBox vBox = new VBox(webView);
    Scene scene = new Scene(vBox, 1200, 600);
    primaryStage.setScene(scene);
    primaryStage.setOnCloseRequest(ev-> canClose.set(true));
    primaryStage.show();
  }

  private String gotoAds(String url, String cookie, String referer) throws IOException {
    OkHttpClient httpClient = new OkHttpClient();
    httpClient.setConnectTimeout(10, TimeUnit.SECONDS);
    httpClient.setReadTimeout(10, TimeUnit.SECONDS);
    Request.Builder requestBuilder = new Request.Builder()
      .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.100 Safari/537.36");
    if(cookie!=null){
      requestBuilder.addHeader("Cookie",cookie);
    }
    if(referer!=null){
      requestBuilder.addHeader("Referer",referer);
    }
    Request request = requestBuilder
      .url(url)
      .build();
    Response response = httpClient.newCall(request).execute();
    if (response != null) {
      return response.body().string();
    }
    return "";
  }

  private String getCookie() throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException {
    CookieManager cookieManager = (CookieManager) CookieHandler.getDefault();
    Field f = cookieManager.getClass().getDeclaredField("store");
    f.setAccessible(true);
    Object cookieStore = f.get(cookieManager);
    Field bucketsField = Class.forName("com.sun.webkit.network.CookieStore").getDeclaredField("buckets");
    bucketsField.setAccessible(true);
    Map buckets = (Map) bucketsField.get(cookieStore);
    f.setAccessible(true);
    final StringBuilder sb = new StringBuilder();
    for (Object o : buckets.entrySet()) {
      Map.Entry entry = (Map.Entry) o;
      String domain = (String) entry.getKey();
      if (domain.contains("exdynsrv.com")) {
        Object[] cookieArray = ((Map) entry.getValue()).values().toArray();
        for (Object object : cookieArray) {
          Field cookieNameField = Class.forName("com.sun.webkit.network.Cookie").getDeclaredField("name");
          cookieNameField.setAccessible(true);
          Field cookieValueField = Class.forName("com.sun.webkit.network.Cookie").getDeclaredField("value");
          cookieValueField.setAccessible(true);
          String cookieName = (String) cookieNameField.get(object);
          String cookieValue = (String) cookieValueField.get(object);
          sb.append(cookieName).append("=").append(cookieValue).append("; ");
        }
      }
    }
    return sb.toString();
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
