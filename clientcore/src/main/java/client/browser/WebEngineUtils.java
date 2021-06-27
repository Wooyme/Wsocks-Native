package client.browser;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.sun.webkit.network.CookieManager;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.html.HTMLElement;
import org.w3c.dom.html.HTMLIFrameElement;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.CookieHandler;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebEngineUtils {
  public final AtomicBoolean allClosed = new AtomicBoolean(false);
  public final AtomicBoolean backgroundFinished = new AtomicBoolean(false);
  private WebEngine webEngine;

  public WebEngineUtils(WebEngine webEngine) {
    this.webEngine = webEngine;
  }

  public void debug(String out) {
    System.out.println(out);
  }

  public void cmd(String cmd){
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(new JsonArray(cmd).getList());
      processBuilder.start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void requestBackground(long timeout) {
    Platform.setImplicitExit(false);
    final long old = new Date().getTime();
    new Thread(() -> {
      try {
        long current = new Date().getTime();
        while(current-old<timeout && !backgroundFinished.get()){
          Thread.sleep(2000);
          current = new Date().getTime();
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      Platform.setImplicitExit(true);
      if (allClosed.get()) System.exit(0);
    }).start();
  }

  public void finishBackground() {
    Platform.setImplicitExit(true);
    backgroundFinished.set(true);
    if (allClosed.get()) System.exit(0);
  }

  public String userAgent() {
    return webEngine.getUserAgent();
  }

  public void localSave(String filename, String content) {
    try {
      FileUtils.writeStringToFile(Paths.get(System.getProperty("user.home"), ".wsocks", filename).toFile(), content);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public String localRead(String filename) {
    try {
      return FileUtils.readFileToString(Paths.get(System.getProperty("user.home"), ".wsocks", filename).toFile());
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public HTMLElement getIframeById(String id) {
    return (HTMLElement) ((HTMLIFrameElement) webEngine.getDocument().getElementById(id)).getContentDocument().getDocumentElement();
  }

  public void ajax(String method, String url, String headers, String mediaType, String requestBody, JSObject cbObj) {
    new Thread(() -> {
      OkHttpClient httpClient = new OkHttpClient();
      httpClient.setConnectTimeout(10, TimeUnit.SECONDS);
      httpClient.setReadTimeout(10, TimeUnit.SECONDS);
      Request.Builder requestBuilder = new Request.Builder();
      new JsonObject(headers).forEach(v -> requestBuilder.addHeader(v.getKey(), (String) v.getValue()));
      requestBuilder.url(url);
      switch (method) {
        case "GET":
          requestBuilder.get();
          break;
        case "POST":
          requestBuilder.post(RequestBody.create(MediaType.parse(mediaType), requestBody));
          break;
        case "PUT":
          requestBuilder.put(RequestBody.create(MediaType.parse(mediaType), requestBody));
          break;
        case "DELETE":
          requestBuilder.delete(RequestBody.create(MediaType.parse(mediaType), requestBody));
          break;
      }
      Request request = requestBuilder.build();
      Platform.runLater(() -> {
        try {
          cbObj.call("success", httpClient.newCall(request).execute());
        } catch (IOException ignored) {
          cbObj.call("error");
        }
      });
    }).start();
  }

  public String getCookie(String _domain) {
    try {
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
        if (domain.contains(_domain)) {
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
    } catch (Throwable e) {
      return null;
    }
  }
}
