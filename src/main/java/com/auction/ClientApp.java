package com.auction;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class ClientApp extends Application {

  @Override
  public void start(Stage primaryStage) {
    Label label = new Label("Auction System - Client");
    label.setStyle("-fx-font-size: 24px;");

    StackPane root = new StackPane(label);
    Scene scene = new Scene(root, 800, 600);

    primaryStage.setTitle("Online Auction System");
    primaryStage.setScene(scene);
    primaryStage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
