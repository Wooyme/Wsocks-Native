package co.zzyun.client;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Date;

public class UIController {
  @FXML
  private ListView<Property> listView;
  @FXML
  private TextField usernameTextField;
  @FXML
  private TextField passwordTextField;
  @FXML
  private Label nodeLabel;
  private Number selected = -1;

  @FXML
  protected void initialize() {
    loadFromFile();
    listView.getSelectionModel()
      .selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue.intValue() < 0) return;
      this.selected = newValue;
    });
  }


  private void saveToFile() {
    File file = new File(Paths.get(System.getProperty("user.dir"), "save.cfg").toString());
    try {
      BufferedWriter writer = new BufferedWriter(new FileWriter(file));
      StringBuilder sb = new StringBuilder();
      String username = this.usernameTextField.getText();
      String password = this.passwordTextField.getText();
      sb.append(username).append("\n").append(password);
      writer.write(sb.toString());
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void loadFromFile() {
    File file = new File(Paths.get(System.getProperty("user.dir"), "save.cfg").toString());
    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      this.usernameTextField.setText(reader.readLine());
      this.passwordTextField.setText(reader.readLine());
      reader.close();
    } catch (IOException ignored) {

    }
  }

  public void onConfirmButtonClicked(ActionEvent actionEvent) {
    Property property = this.listView.getItems().get(selected.intValue());
    nodeLabel.setText(property.toString());
    ((Stage) usernameTextField.getScene().getWindow()).setTitle("正在连接");
    new Thread(() -> {
      try {
        URL url = new URL("http://127.0.0.1:1078/connect?host=" + property.getHost()
          + "&port=" + property.getPort());
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        final String msg = con.getResponseMessage();
        if (con.getResponseCode() == 200) {
          Platform.runLater(() -> ((Stage) usernameTextField.getScene().getWindow()).setTitle("连接成功"));
        } else {
          Platform.runLater(() -> ((Stage) usernameTextField.getScene().getWindow()).setTitle("连接失败," + msg));
        }
        con.disconnect();

      } catch (IOException ignored) {
      }
    }).start();
  }

  public void onLoginButtonClicked(ActionEvent actionEvent) {
    String username = usernameTextField.getText();
    String password = passwordTextField.getText();
    saveToFile();
    ((Stage) usernameTextField.getScene().getWindow()).setTitle("正在获取列表");
    new Thread(() -> {
      try {
        String timestamp = String.valueOf(new Date().getTime());
        String version = System.getProperty("version");
        String salt = System.getProperty("salt");
        String secret = DigestUtils.md5Hex(timestamp + version + salt);
        URL url = new URL(System.getProperty("center_url") + "/hosts?t=" + timestamp + "&v=" + version + "&s=" + secret);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        final String msg = con.getResponseMessage();
        if (con.getResponseCode() == 200) {
          Platform.runLater(() -> {
            con.getHeaderFields().forEach((k, v) -> {
              if (k!=null && k.startsWith("x-host")) {
                Property p = Property.fromLocalString(v.get(0));
                this.listView.getItems().add(p);
              }
            });
            ((Stage) usernameTextField.getScene().getWindow()).setTitle("列表获取成功");
          });
        } else {
          Platform.runLater(() -> ((Stage) usernameTextField.getScene().getWindow()).setTitle("列表获取失败," + msg));
        }
        con.disconnect();
      } catch (IOException ignored) {
      }
      Platform.runLater(() -> ((Stage) usernameTextField.getScene().getWindow()).setTitle("正在登录"));
      try {
        URL url = new URL("http://127.0.0.1:1078/start?center_host=" + System.getProperty("center_host")
          + "&center_port=" + System.getProperty("center_port")
          + "&user=" + username + "&pass=" + password);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        final String msg = con.getResponseMessage();
        if (con.getResponseCode() == 200) {
          Platform.runLater(() -> ((Stage) usernameTextField.getScene().getWindow()).setTitle("登录成功"));
        } else {
          Platform.runLater(() -> ((Stage) usernameTextField.getScene().getWindow()).setTitle("登录失败," + msg));
        }
        con.disconnect();

      } catch (IOException ignored) {
      }

    }).start();

  }
}
