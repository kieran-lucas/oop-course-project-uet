package com.auction;

import com.auction.config.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Entry point của ứng dụng JavaFX client.
 *
 * Khởi tạo SceneManager (Singleton) rồi navigate đến màn login.
 * Từ đây trở đi, mọi chuyển màn đều qua:
 *   SceneManager.getInstance().navigateTo("ten.fxml");
 */
public class ClientApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Khởi tạo SceneManager — chỉ gọi 1 lần duy nhất ở đây
        SceneManager.init(primaryStage, 1200, 800);

        primaryStage.setTitle("Online Auction System");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        // Navigate đến màn đăng nhập (lazy load lần đầu)
        SceneManager.getInstance().navigateTo("login.fxml");

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}