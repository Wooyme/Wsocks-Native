package co.zzyun.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Paths;
import java.util.Scanner;

public class UIController {
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
//  @FXML
//  private CheckBox doZipCheckBox;
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
    UI.vertx.eventBus().consumer("status-modify",m->{
      String s = ((JsonObject) m.body()).getString("status");
      System.out.println("Modify:"+s);
      Platform.runLater(()->{
        UI.tray.updateMenu(0,s);
      });
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
//      doZipCheckBox.setSelected(false);
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
    Property property = new Property(host,remotePort,username,password);
    UI.vertx.eventBus().send("config-modify",property.toJson());
    ((Stage) remoteAddressTextField.getScene().getWindow()).close();
  }
}
