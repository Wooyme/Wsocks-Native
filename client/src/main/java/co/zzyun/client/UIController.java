package co.zzyun.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Scanner;

public class UIController {
  @FXML
  public Label statusLabel;
  @FXML
  private ListView<Property> listView;
  @FXML
  private TextField remoteAddressTextField;
  @FXML
  private TextField remotePortTextField;
  @FXML
  private TextField usernameTextField;
  @FXML
  private TextField passwordTextField;

  private Number selected = -1;


  @FXML
  protected void initialize() {
    loadFromJson();
    listView.getSelectionModel()
      .selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue.intValue() < 0) return;
      this.selected = newValue;
      setProperty();
    });
  }
  @FXML
  protected void onAddButtonClicked(ActionEvent event) {
    String host = remoteAddressTextField.getText();
    Integer remotePort = new Integer(remotePortTextField.getText());
    String username = usernameTextField.getText();
    String password = passwordTextField.getText();
    listView.getItems().add(new Property(host,remotePort,username,password));
    this.selected = listView.getItems().size()-1;
    saveToJson();
  }

  public void onRemoveButtonClicked(ActionEvent actionEvent) {
    if(this.selected.intValue()<0) return;
    listView.getItems().remove(this.selected.intValue());
    if(listView.getItems().size()==0) this.selected = -1;
    else{
      this.selected = 0;
      this.listView.getSelectionModel().select(this.selected.intValue());
    }
    setProperty();
    saveToJson();
  }

  private void setProperty() {
    Property property;
    try {
      property = this.listView.getItems().get(this.selected.intValue());
    }catch (Throwable e){
      remoteAddressTextField.clear();
      remotePortTextField.clear();
      usernameTextField.clear();
      passwordTextField.clear();
      return;
    }
    remoteAddressTextField.setText(property.getHost());
    remotePortTextField.setText(String.valueOf(property.getPort()));
    usernameTextField.setText(property.getUsername());
    passwordTextField.setText(property.getPassword());
  }

  private void saveToJson(){
    JsonArray array = new JsonArray();
    File file = new File(Paths.get(System.getProperty("user.dir"),"save.json").toString());
    this.listView.getItems().forEach(p->{
      array.add(p.toJson());
    });
    try {
      BufferedWriter writer = new BufferedWriter(new FileWriter(file));
      writer.write(array.encode());
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void loadFromJson(){
    File file = new File(Paths.get(System.getProperty("user.dir"),"save.json").toString());
    try {
      String content = new Scanner(file).useDelimiter("\\Z").next();
      new JsonArray(content).forEach(p-> this.listView.getItems().add(Property.fromJson((JsonObject) p)));
    } catch (FileNotFoundException e) { }
  }

  public void onConfirmButtonClicked(ActionEvent actionEvent) {
    String host = remoteAddressTextField.getText();
    Integer remotePort = new Integer(remotePortTextField.getText());
    String username = usernameTextField.getText();
    String password = passwordTextField.getText();
    try {
      URL url = new URL("http://localhost:1088/index?host="+host+"&port="+remotePort+"&user="+username+"&pass="+password);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setRequestMethod("GET");
      if(con.getResponseCode()==200){
        statusLabel.setText("连接成功");
        //((Stage) remoteAddressTextField.getScene().getWindow()).close();
      }else{
        statusLabel.setText("连接失败:"+con.getResponseMessage());
      }
      con.disconnect();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }
}
