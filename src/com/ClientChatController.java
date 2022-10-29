package com;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

public class ClientChatController implements Initializable {
    @FXML
    public Label lbError;
    @FXML
    private Button btnSend;
    @FXML
    private Label lbNameConnect;
    @FXML
    private ListView<HBox> lvContent;
    @FXML
    private TextField txtMessage;

    private ObservableList<HBox> contentList;

    private String name;

    ListContactController listContactController;

    public Label getLbNameConnect() {
        return lbNameConnect;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setListContactController(String name, ListContactController listContactController) {
        this.listContactController = listContactController;
        this.name = name;
        lbNameConnect.setText("Chat with " + name.toUpperCase(Locale.ROOT));
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        contentList = FXCollections.observableArrayList();
        lvContent.setItems(contentList);

        txtMessage.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                btnSend.setDisable(t1.trim().isEmpty());
            }
        });

        txtMessage.setOnKeyReleased(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent keyEvent) {
                handleKeyMessage(keyEvent);
            }
        });

        btnSend.setDisable(true);

    }

    /**
     * Gửi dữ liệu tới Server (khi người sử dụng ấn vào nút bấm “Send”)
     * nối vào khung chat nội dung gửi
     */
    @FXML
    public void btnSendActionPerformed() {
        String chat = listContactController.getName() + ":" + txtMessage.getText();
        if (chat.contains("/./")) {
            lbError.setText("Tin nhắn không được chứa đoạn /./");
            return;
        } else {
            lbError.setText("");
        }
        addContent(chat);
        txtMessage.clear();
        listContactController.send(ListContactController.HEADER_SEND_TO, name + "/./" + chat);
    }

    /**
     * khi nhấn enter ở tin nhắn thì gọi hàm gửi nếu ô nhắn ko rỗng
     *
     * @param keyEvent sự kiện nhấn chuột
     */
    @FXML
    public void handleKeyMessage(KeyEvent keyEvent) {
        if (keyEvent.getCode().equals(KeyCode.ENTER)) {
            if (txtMessage.getText().trim().isEmpty()) {
                return;
            }
            btnSendActionPerformed();
        }
    }

    /**
     * thêm nội dung chat vào listView đang hiển thị, phù hợp với thêm 1 đoạn tin nhắn 1 lần
     *
     * @param content
     */
    public void addContent(String content) {
        addContent2(content);
        listViewRefresh();

    }

    /**
     * sửa lỗi listview ko cập nhật vị trí cuộn cuối cùng
     */
    public void listViewRefresh() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < lvContent.getItems().size(); i++) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            lvContent.scrollTo(lvContent.getItems().size() + 2);
                        }
                    });
                }

                for (int i = 0; i < lvContent.getItems().size() / 10; i++) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            lvContent.scrollTo(lvContent.getItems().size() + 2);
                        }
                    });
                    lvContent.refresh();

                }
            }
        }).start();
//        Platform.runLater(new Runnable() {
//            @Override
//            public void run() {
////                lvContent.scrollTo(lvContent.getItems().size() + 10);
//
//            }
//        });


    }

    /**
     * thêm nội dung chat vào list view đang hiển thị, phù hợp với thêm liên tục nhiều đoạn chat/ gọi hàm liên tục như
     * lấy lịch sử chat và thêm tất cả các đoạn trong lịch sử vào
     * sau khi gọi hàm liên tục xong cần xử lý thêm sửa lỗi listview ở nơi gọi
     *
     * @param content
     */
    public void addContent2(String content) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                // lấy nội dung đoạn chat bỏ phần đầu là tên người phân cách bằng dấu :
                Label lbContent = new Label(content.split(":")[1]);
                lbContent.setPadding(new Insets(5, 20, 5, 20));
                lbContent.setWrapText(true);

                // chỉ cho kích thước đoạn chat bằng 1 nửa listview
                lbContent.setMaxWidth(lvContent.getWidth() / 2);

                // tạo menu item tên copy để thực hiện copy text của label vào hệ thống
                MenuItem copyItem = new MenuItem("Copy");
                copyItem.setOnAction(event -> {
                    final ClipboardContent clipboardContent = new ClipboardContent();
                    clipboardContent.putString(lbContent.getText());
                    Clipboard.getSystemClipboard().setContent(clipboardContent);
                });
                ContextMenu copyMenu = new ContextMenu(copyItem);
                // thêm copy menu vào label để thực hiện lệnh copy
                lbContent.setContextMenu(copyMenu);

                lbContent.setStyle("-fx-text-fill: white; -fx-font-size: 16; -fx-background-radius: 8; -fx-font-weight: bold");

                // thêm lbContent vào hbox
                HBox hBox = new HBox(lbContent);

                // nếu đầu đoạn chat có tên là user cần chat chính là tên cửa sổ này
                // thì cho đoạn chat sang trái hbox, màu nền xám
                // ngược lại là tên chính người dùng này thì cho sang bên phải, màu nền xanh
                if (content.startsWith(listContactController.getName().toString().trim())) {
                    hBox.setAlignment(Pos.CENTER_RIGHT);
                    lbContent.setStyle("-fx-background-color: green;" + lbContent.getStyle());

                } else {
                    hBox.setAlignment(Pos.CENTER_LEFT);
                    lbContent.setStyle("-fx-background-color: grey;" + lbContent.getStyle());
                }
                // thêm hbox vào list
                contentList.add(hBox);
                // cho ListView cuộn xuống vị trí cuối cùng
                lvContent.scrollTo(hBox);

            }
        });

    }

    public ListView<HBox> getLvContent() {
        return lvContent;
    }

}
