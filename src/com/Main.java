package com;

import Dao.NameDao;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.Objects;

public class Main extends Application {

    @Override
    public void init() throws Exception {
        super.init();
        // đọc dữ liệu từ file vào biến dùng chung khi ứng dụng khởi động
        NameDao.getInstance().readNameFromFile();
    }

    @Override
    public void start(Stage primaryStage) throws Exception{
//        Parent root = FXMLLoader.load(getClass().getResource("../ui/clientChat.fxml"));
        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/ui/listContact.fxml")));
        primaryStage.setTitle("Client Chat");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();

        // đóng cửa sổ thoát luôn chương trình
        primaryStage.setOnHiding(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent windowEvent) {
                System.exit(0);
            }
        });
    }


    public static void main(String[] args) {
        launch(args);
    }
}
