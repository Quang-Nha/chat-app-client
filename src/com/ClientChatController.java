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
     * G???i d??? li???u t???i Server (khi ng?????i s??? d???ng ???n v??o n??t b???m ???Send???)
     * n???i v??o khung chat n???i dung g???i
     */
    @FXML
    public void btnSendActionPerformed() {
        String chat = listContactController.getName() + ":" + txtMessage.getText();
        if (chat.contains("/./")) {
            lbError.setText("Tin nh???n kh??ng ???????c ch???a ??o???n /./");
            return;
        } else {
            lbError.setText("");
        }
        addContent(chat);
        txtMessage.clear();
        listContactController.send(ListContactController.HEADER_SEND_TO, name + "/./" + chat);
    }

    /**
     * khi nh???n enter ??? tin nh???n th?? g???i h??m g???i n???u ?? nh???n ko r???ng
     *
     * @param keyEvent s??? ki???n nh???n chu???t
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
     * th??m n???i dung chat v??o listView ??ang hi???n th???, ph?? h???p v???i th??m 1 ??o???n tin nh???n 1 l???n
     *
     * @param content
     */
    public void addContent(String content) {
        addContent2(content);
        listViewRefresh();

    }

    /**
     * s???a l???i listview ko c???p nh???t v??? tr?? cu???n cu???i c??ng
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
     * th??m n???i dung chat v??o list view ??ang hi???n th???, ph?? h???p v???i th??m li??n t???c nhi???u ??o???n chat/ g???i h??m li??n t???c nh??
     * l???y l???ch s??? chat v?? th??m t???t c??? c??c ??o???n trong l???ch s??? v??o
     * sau khi g???i h??m li??n t???c xong c???n x??? l?? th??m s???a l???i listview ??? n??i g???i
     *
     * @param content
     */
    public void addContent2(String content) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                // l???y n???i dung ??o???n chat b??? ph???n ?????u l?? t??n ng?????i ph??n c??ch b???ng d???u :
                Label lbContent = new Label(content.split(":")[1]);
                lbContent.setPadding(new Insets(5, 20, 5, 20));
                lbContent.setWrapText(true);

                // ch??? cho k??ch th?????c ??o???n chat b???ng 1 n???a listview
                lbContent.setMaxWidth(lvContent.getWidth() / 2);

                // t???o menu item t??n copy ????? th???c hi???n copy text c???a label v??o h??? th???ng
                MenuItem copyItem = new MenuItem("Copy");
                copyItem.setOnAction(event -> {
                    final ClipboardContent clipboardContent = new ClipboardContent();
                    clipboardContent.putString(lbContent.getText());
                    Clipboard.getSystemClipboard().setContent(clipboardContent);
                });
                ContextMenu copyMenu = new ContextMenu(copyItem);
                // th??m copy menu v??o label ????? th???c hi???n l???nh copy
                lbContent.setContextMenu(copyMenu);

                lbContent.setStyle("-fx-text-fill: white; -fx-font-size: 16; -fx-background-radius: 8; -fx-font-weight: bold");

                // th??m lbContent v??o hbox
                HBox hBox = new HBox(lbContent);

                // n???u ?????u ??o???n chat c?? t??n l?? user c???n chat ch??nh l?? t??n c???a s??? n??y
                // th?? cho ??o???n chat sang tr??i hbox, m??u n???n x??m
                // ng?????c l???i l?? t??n ch??nh ng?????i d??ng n??y th?? cho sang b??n ph???i, m??u n???n xanh
                if (content.startsWith(listContactController.getName().toString().trim())) {
                    hBox.setAlignment(Pos.CENTER_RIGHT);
                    lbContent.setStyle("-fx-background-color: green;" + lbContent.getStyle());

                } else {
                    hBox.setAlignment(Pos.CENTER_LEFT);
                    lbContent.setStyle("-fx-background-color: grey;" + lbContent.getStyle());
                }
                // th??m hbox v??o list
                contentList.add(hBox);
                // cho ListView cu???n xu???ng v??? tr?? cu???i c??ng
                lvContent.scrollTo(hBox);

            }
        });

    }

    public ListView<HBox> getLvContent() {
        return lvContent;
    }

}
