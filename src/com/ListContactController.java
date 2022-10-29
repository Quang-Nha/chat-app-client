package com;

import Dao.NameDao;
import entity.Server;
import entity.User;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.*;
import java.util.function.Predicate;

public class ListContactController implements Initializable {
    private static final String HEADER_REPLACE_LOGIN = "REPLACE_LOGIN";
    public static final String HEADER_NAME = "NAME";
    public static final String HEADER_USERS = "USERS";
    public static final String HEADER_CHAT_HISTORY = "CHAT_HISTORY";
    public static final String HEADER_SEND_TO = "SEND_TO";
    private static final String HEADER_SEND_FROM = "SEND_FROM";
    private static final String HEADER_CHANGE_PASSWORD = "CHANGE_PASSWORD";
    private static final String HEADER_RENAME = "RENAME";
    private static final String HEADER_USER_RENAME = "USER_RENAME";

    @FXML
    public Button btnChangePass;
    @FXML
    public Button btnChangeServerIp;
    @FXML
    public Button btnLogout;
    @FXML
    public TextField tfSearch;
    @FXML
    public Label lbIPServer;
    @FXML
    private ListView<User> lvContact;
    @FXML
    private Label lbName;

    private Socket socket;
    private StringBuilder name;
    private User user;
    private DataInputStream dis;
    private DataOutputStream dos;

    FilteredList<User> filteredList;
    Predicate<User> predicate;

    // list chứa danh sách các liên hệ
    private final ObservableList<User> userlList = FXCollections.observableArrayList();
    // lưu controller của các cửa sổ chat đang mở
    private final Map<String, ClientChatController> clientChatControllerMap = new HashMap<>();
    private boolean loginReplace;
    private boolean isLogout;
    private boolean isIpChange;
    private boolean isStartup;
    private boolean closeOldSocket;
    private boolean isConnectError;

    // map chứa các dialog hoặc alert đang mở
    private Map<Dialog, Dialog> openingDialogMap;
    private boolean isOpenErrorAlert;

    public StringBuilder getName() {
        return name;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        name = new StringBuilder("");
        lbName.setText("Name?");
        openingDialogMap = new HashMap<>();

        // biến xác nhận là thời điểm khởi động ứng dụng để khi có lỗi kết nối xác định lỗi khi khởi động hay ko
        isStartup = true;

        // tạo các list lọc ko hiển thị tên chính user này trong list và xắp xếp ràng buộc với userlList
        filteredList = userlList.filtered(new Predicate<User>() {
            @Override
            public boolean test(User user) {
                if (user.getUsername().equalsIgnoreCase(name.toString())) {
                    return false;
                }
                return true;
            }
        });

        SortedList<User> sortedList = filteredList.sorted(new Comparator<User>() {
            @Override
            public int compare(User o1, User o2) {
                if (!o1.isConnecting() && o2.isConnecting()) {
                    return 1;
                } else if (o1.isConnecting() && !o2.isConnecting()) {
                    return -1;
                }
                return 0;
            }
        });

        // ràng buộc ListView với list sortedList
        lvContact.setItems(sortedList);

        setCellFactory();

        // bắt đầu kết nối socket với server
        connect();

        // khi click vào list view thì gọi hàm xử lý
        lvContact.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {

                handleClickUser(mouseEvent);

            }
        });
    }

    /**
     * nhập id máy chủ mới và gọi hàm connect() để kết nối theo id này
     */
    private void ipInput() {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {

                // sửa lại biến nhớ sửa ip về true để xác nhận sự kiện đổi ip
                isIpChange = true;

                Dialog<ButtonType> dialog = inputDialog("Xác nhận IP máy chủ mới",
                        "Nhập địa chỉ IP của máy chủ mới hoặc thoát");

                TextField tfIP = new TextField();
                tfIP.setPromptText("Nhập IP máy chủ");
                Label lbError = new Label();
                lbError.setTextFill(Color.RED);
                VBox vBox = new VBox(tfIP, lbError);

                dialog.getDialogPane().setContent(vBox);

                while (true) {
                    // thêm vào map dialog đang mở này
                    openingDialogMap.put(dialog, dialog);
                    System.out.println(openingDialogMap.size());

                    Optional<ButtonType> result = dialog.showAndWait();

                    // đóng cửa sổ bằng bất cứ nút gì thì xóa dialog khỏi map đang mở
                    openingDialogMap.remove(dialog);
                    System.out.println(openingDialogMap.size());
                    // nếu đang là sự kiện lỗi kết nối thì ko làm gì
                    if (isOpenErrorAlert) {
                        return;
                    }


                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        String ip = tfIP.getText();
                        if (ip.trim().isEmpty()) {
                            lbError.setText("Hãy nhập địa chỉ IP");
                        }
                        // nếu ip nhập vào ko rỗng thì lưu lại địa chỉ vào biến, lưu biến vào file
                        // và gọi hàm kết nối lại
                        else {
                            // nếu địa chỉ ip nhập vẫn giống ip đang dùng và ko có lỗi kết nối
                            // thì xem đã đăng nhập chưa, nếu chưa thì gọi hàm login, ko thì thoát hàm
                            // cho biến xác nhận đổi ip về false
                            if (ip.equals(Server.hostIp) && !isConnectError) {
                                isIpChange = false;
                                if (name.length() == 0) {
                                    login();
                                }
                                return;

                            }

                            Server.hostIp = ip;

                            // lưu giá trị ip từ biến vào file
                            Server.saveIpServerToFile();

                            // đóng socket kết nối hiện tại rồi gọi hàm kết nối connect() nếu socket ko null tức đang kết nối
                            // nếu không đóng thì khi gọi hàm kết nối socket sẽ được khởi tạo lại
                            // nghĩa là nó sẽ được gán 1 vùng nhớ mới, còn vùng nhớ cũ vẫn còn
                            // khi đó kết nối với máy chủ cũ ko bị mất nhưng ko còn sử dụng
                            // máy chủ cũ cũng vẫn nghĩ là đang user này đang online
                            // nên bắt buộc phải đóng kêt nối
                            // rồi gán biến xác nhận đóng socket cũ là true để xử lý sự kiện lỗi đọc luồng
                            // gửi về nó sẽ biết do đóng socket cũ nó sẽ ko gọi hàm báo lỗi nữa
                            // rồi mới gọi hàm connect để chắc chắn lỗi xong socket cũ set socket = null rồi
                            // mới gọi kết nối soc ket cũ,
                            // chứ gọi connect mới và đóng socket cũ gần như cùng lúc
                            // thì có thể gây ra trường hợp socket mới tạo xong rồi mà hàm xử lý lỗi đọc socket cũ
                            // mới chạy nó gán lại socket mới = null. lúc này vùng nhớ socket mới vừa tạo bị che đi
                            // vì nó bị gán đối tượng mới là null nên không truy cập được nữa
                            // và nó vẫn duy trì kết nối, khi đổi máy chủ khác không thể
                            // tắt kết nối này đi được làm máy chủ cũ vẫn tưởng đang kết nối

                            // chỉ gọi thông báo do kết nối mới/ko phải kết nối cũ bị lỗi thôi

                            // nếu socket đang ko kết nối tức = null(khi bị lỗi kết nối nó bị gán lại = null)
                            // thì gọi luôn hàm kết nối connect()
                            System.out.println(socket);
                            System.out.println(Server.hostIp);
                            if (socket != null) {
                                closeOldSocket = true;
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                connect();
                            }

                            break;
                        }
                    }
                    // khi đóng cửa sổ bằng nút cancel
                    // cho biến xác nhận đổi ip về false
                    // xác định xem có phải đang có lỗi kết nối ko, nếu đúng thì thoát luôn chương trình

                    // nếu không thì vẫn đang kết nối, lúc này cần xem đã đăng nhập chưa
                    // nếu chưa thì gọi hàm login, rồi thì ko gọi
                    // cuối cùng là thoát vòng lặp, thoát hàm
                    else {

                        isIpChange = false;

                        if (isConnectError) {
                            System.exit(0);
                        } else {
                            if (name.length() == 0) {
                                login();
                            }
                            return;
                        }
                    }
                }
            }
        });

    }

    /**
     * kết nối với máy chủ
     * tạo socket rồi tạo các luồng đọc ghi
     * gọi hàm read luôn chờ dữ liệu từ server gửi về
     * gọi hàm đăng nhập
     */
    private void connect() {
        // nếu biến lưu IP rỗng thì hiển thị ip server là localhost
        // ko thì hiển thị IP của server
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (Server.hostIp.trim().isEmpty() || Server.hostIp.equalsIgnoreCase("localhost")
                        || Server.hostIp.equals("127.0.0.1")) {
                    lbIPServer.setText("localhost(127.0.0.1)");
                } else {
                    lbIPServer.setText(Server.hostIp);
                }
            }
        });

        // bắt đầu kết nối với server thông qua ip và port của nó
        try {
            socket = new Socket(Server.hostIp, Server.POST);

            // thiết lập các luồng vào, ra theo socket
            this.dis = new DataInputStream(this.socket.getInputStream());
            this.dos = new DataOutputStream(this.socket.getOutputStream());

            // nếu ko có lỗi kết nối thì sẽ gọi ra hàm đăng nhập
            login();

            // bỏ xác nhận thời điểm khởi động
            isStartup = false;

            // xác nhận kết nối thành công không còn là nhập ip nữa, cho biến nhớ vè false
            isIpChange = false;

            isConnectError = false;

            // tạo luồng đọc dữ liệu gửi về
            new Thread(new Runnable() {
                @Override
                public void run() {
                    read();
                }
            }).start();

            System.out.println(socket);
        } catch (IOException e) {
            // mất kết nối cho socket về null luôn
            socket = null;
            System.out.println("connect error");

            // khi bị lỗi kết nối gọi hàm xử lý lỗi
            connectError();
            e.printStackTrace();
        }


    }

    /**
     * alert
     *
     * @param title
     * @param headerText
     * @param isError    là dạng báo lỗi hay thông báo
     * @return
     */
    private Alert alertFc(String title, String headerText, boolean isError) {
        Alert alert;
        if (isError) {
            alert = new Alert(Alert.AlertType.ERROR);
        } else {
            alert = new Alert(Alert.AlertType.INFORMATION);
        }

        alert.setTitle(title);
        alert.setHeaderText(headerText);

        return alert;
    }

    /**
     * xử lý khi không thể kết nối với máy chủ
     * xác nhận lỗi do logout, máy chủ tắt, bị đăng nhập chèn, hay khởi động ko xác nhận được máy chủ thông qua
     * các biến xác nhận thì hiển thị thông báo theo lỗi tương ứng
     * lỗi do logout, bị đăng nhập chèn thì connect và login lại
     * lỗi do máy chủ tắt, khởi động ko xác nhận được máy chủ thì nhập lại IP rồi connect và login lại
     */
    private void connectError() {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {

                // set biến xác nhận lỗi kết nối về true
                isConnectError = true;

                // xác nhận đang mở cửa sổ báo lỗi để khi cửa sổ này đóng tất cả cửa sổ khác khi có lỗi
                // nó sẽ xem biến này và không làm gì khi đóng
                isOpenErrorAlert = true;

                // đóng tất cả các dialog hoặc alert đang mở để tránh lỗi
                for (Dialog dialog : openingDialogMap.values()) {
                    dialog.close();
                }

//                lbIPServer.setText("?????");

                // nếu socket gây ra lỗi tức ko kết nối được với server
                // mặc định setHeaderText là máy chủ bị tắt
                // sau đó có xem xét các biến nhớ là logout hay đăng nhập chèn mà đổi lại thông báo
                // thì thông báo và thoát chương trình
                Alert alert = alertFc("Lỗi kết nối",
                        "Máy chủ đã bị tắt\nKhông thể kết nối\nĐổi địa chỉ IP máy chủ mới hoặc thoát",
                        true);
                alert.initOwner(lvContact.getScene().getWindow());

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (isLogout) {
                    alert.setTitle("Lỗi kết nối");
                    alert.setHeaderText("Đã thoát tài khoản\nThoát chương trình hoặc đăng nhập lại");
                }

                // nếu là thời điểm khởi động ứng dụng hoặc do nhập ip
                // thì thông báo không kết nối được với IP lấy từ file hoặc người dùng nhập
                if (isStartup || isIpChange) {
                    alert.setTitle("Lỗi kết nối");
                    alert.setHeaderText("IP máy chủ đang sử dụng không đúng hoặc máy chủ đang tắt\nNhập IP máy chủ mới hoặc thoát");
                }


                // nếu có tín hiệu mất kết nối do bị đăng nhập nơi khác nhờ biến xác nhận loginReplace
                // nên máy chủ đóng kết nối của user này
                // thì đổi nội dung thông báo thành bị đăng nhập nơi khác
                // tín hiệu logout và đổi IP cũng thay đổi nội dung thông báo theo tín hiệu
                if (loginReplace) {
                    alert.setTitle("Lỗi kết nối");
                    alert.setHeaderText("Tài khoản đã bị đăng nhập nơi khác\nThoát chương trình hoặc đăng nhập lại");
                }


                // set lại tên và biến tên
                name.setLength(0);
                lbName.setText("Name? Không có kết nối");

                // đóng tất cả các cửa sổ chat đang mở bằng cách
                // lấy ra controller của các cửa sổ chat đang mở
                // lấy ra 1 control của nó ví dụ {@link Label} rồi lấy ra scene, từ scene lấy ra window
                // ép kiểu window về Stage rồi gọi lệnh đóng cửa sổ
                for (ClientChatController controller : clientChatControllerMap.values()) {
                    Stage window = (Stage) controller.getLbNameConnect().getScene().getWindow();
                    // chạy trong UiThread nếu ko sẽ có lỗi
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            window.close();
                        }
                    });
                }

                alert.show();


                alert.setOnHiding(new EventHandler<DialogEvent>() {
                    @Override
                    public void handle(DialogEvent dialogEvent) {
                        isOpenErrorAlert = false;

                        // mất kết nối ko do logout hay đăng nhập nơi khác nghĩa là tự máy chủ ngắt hoặc gọi hàm đổi IP máy chủ
                        // thì mở cửa sổ nhập lại ip máy chủ mới khi đóng dialog và set biến xác nhận đổi IP về false
                        // nếu mất kết nối do logout hoặc đăng nhập nơi khác sau khi đóng dialog
                        // ko đóng chương trình mà gọi hàm kết nối lại với server để sẵn sàng đăng nhập rồi
                        // conect gọi hàm login để đăng nhập lại
                        // và set lại logout và loginReplace = false
                        if (isLogout || loginReplace) {
                            isLogout = false;
                            loginReplace = false;
                            connect();
                        }
                        // tự máy chủ ngắt, khởi động ko kết nối được hoặc gọi hàm đổi IP máy chủ
                        // thì mở cửa sổ nhập lại ip máy chủ mới khi đóng dialog và set biến xác nhận đổi IP về false
                        else {

                            ipInput();

                        }

                    }
                });


            }
        });


    }

    /**
     * kiểm tra các điều kiện đăng nhập
     */
    private void login() {
        // lấy tên user đọc từ file
        String namePass = NameDao.getInstance().getNamePass().toString();
        String[] arrNamePass = namePass.split(":");

        String pass = "";
        String sName = "";

        if (arrNamePass.length > 0) {
            sName = arrNamePass[0];
        }
        if (arrNamePass.length == 2) {
            pass = arrNamePass[1];
        }

        // tạo luồng xác định tên
        String finalPass = pass;
        String finalSName = sName;
        Platform.runLater(new Runnable() {
            @Override
            public void run() {

                // cần ngủ 100ms vì đợi thoát khỏi hàm init thì ứng dụng mới chạy và lấy được window cho dialog
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }


                // tạo dialog yêu cầu nhập tên người dùng gắn với window của  ứng dụng
                Dialog<ButtonType> dialog = inputDialog("Đăng nhập, tạo mới, đổi ip"
                        , "Đăng nhập. Tạo mới tài khoản\nHoặc đổi IP máy chủ");

                // tạo các control để người dùng nhập thông tin
                Label lbNameInput = new Label("Username(not contain _ or .txt):");
                TextField tfName = new TextField();
                tfName.setText(finalSName);
                tfName.setPromptText("Enter username");
                Label lbPass = new Label("Password:");
                PasswordField passwordField = new PasswordField();
                passwordField.setPromptText("Enter password");
                passwordField.setText(finalPass);
                CheckBox checkBox = new CheckBox("Save password?");
                checkBox.setSelected(true);
                Label lbError = new Label();
                lbError.setTextFill(Color.RED);

                // thêm nút đổi ip máy chủ
                Button btnChangeIpServer = new Button("Đổi IP máy chủ");

                btnChangeIpServer.setStyle("-fx-background-radius: 5; -fx-background-color: Green" +
                        "; -fx-padding: 5 10 5 10; -fx-text-fill: white; -fx-font-weight: bold");

                HBox hBox = new HBox(btnChangeIpServer);
                hBox.setAlignment(Pos.CENTER_RIGHT);


                // set sự kiện đổi ip cho biến xác nhận là true
                // đóng dialog này và mở dialog nhập ip
                btnChangeIpServer.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent event) {
                        // xác nhận trước biến xác định nhập ip để khi đóng dialog này ko thoát luôn chương trình
                        isIpChange = true;
                        dialog.close();
                        ipInput();
                    }
                });

                // thêm các control vào vbox
                VBox vBox = new VBox(lbNameInput, tfName, lbPass, passwordField, checkBox, lbError, hBox);

                vBox.setSpacing(5);
                VBox.setMargin(vBox, new Insets(15));// set Margin

                // thêm vBox trên vào dialog
                dialog.getDialogPane().setContent(vBox);

                // lặp đến khi đủ điều kiện thoát
                while (true) {
                    // thêm vào map dialog đang mở này
                    openingDialogMap.put(dialog, dialog);
                    System.out.println(openingDialogMap.size());

                    // hiển thị dialog và lấy kết quả trả về
                    Optional<ButtonType> result = dialog.showAndWait();

                    // đóng cửa sổ bằng bất cứ nút gì thì xóa dialog khỏi map đang mở
                    openingDialogMap.remove(dialog);
                    System.out.println(openingDialogMap.size());
                    // nếu đang là sự kiện lỗi kết nối thì ko làm gì
                    if (isOpenErrorAlert) {
                        return;
                    }

                    // nếu nhấn nút ok
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        // lấy tên người dùng nhập gán cho sName
                        String sName = tfName.getText();
                        // lấy pass người dùng nhập gán cho pass
                        String pass = passwordField.getText();

                        // tạo User với các thông tin lấy được
                        User checkUser = new User(sName, pass, true);

                        // nếu cả 2 thông tin ko rỗng thì kiểm tra các điều kiện
                        if (!sName.trim().isEmpty() && !pass.trim().isEmpty()) {

                            // nếu tên chứa 2 kí tự _ và .txt thì thông báo ko được và nhảy sang vòng lặp tiếp
                            // để yêu cầu nhập lại
                            if (sName.contains("_") || sName.contains(".txt")) {
                                lbError.setText("Tên không được chứa kí tự _ hoặc .txt");
                                continue;
                            }

                            // xem có trùng với liên hệ nào trong list ko
                            boolean check = false;
                            User userServer = null;
                            for (User s : userlList) {
                                if (s.equals(checkUser)) {
                                    check = true;
                                    userServer = s;
                                    break;
                                }
                            }

                            // nếu trùng thì xem có trùng cả pass ko
                            if (check) {
                                // nếu có thì pass hiển thị alert thông báo đăng nhập thành công và thoát vòng lặp
                                if (userServer.equals2(checkUser)) {
                                    user = checkUser;
                                    Alert alert = alertFc("Thông tin đăng nhập", "Chào mừng " + user.getUsername()
                                            + "\nĐăng nhập thành công", false);

                                    // thêm vào map alert đang mở này
                                    openingDialogMap.put(alert, alert);
                                    System.out.println(openingDialogMap.size());

                                    alert.setOnHiding(new EventHandler<DialogEvent>() {
                                        @Override
                                        public void handle(DialogEvent dialogEvent) {
                                            // đóng cửa sổ bằng bất cứ nút gì thì xóa alert khỏi map đang mở
                                            openingDialogMap.remove(alert);
                                            System.out.println(openingDialogMap.size());

                                        }
                                    });

                                    alert.show();

                                    break;
                                }
                                // nếu ko thì sai mật khẩu ko thoát khỏi vòng lặp
                                else {
                                    lbError.setText("Tên trùng khớp nhưng sai mật khẩu");
                                }
                            }
                            // nếu ko thì là tạo tài khoản mới, hiển thị alert đăng ký thành công và thoát vòng lặp
                            else {
                                user = checkUser;
                                Alert alert = alertFc("Thông tin đăng ký", "Chào mừng " + user.getUsername()
                                        + "\nĐăng ký thành công", false);

                                // thêm vào map alert đang mở này
                                openingDialogMap.put(alert, alert);
                                System.out.println(openingDialogMap.size());

                                alert.setOnHiding(new EventHandler<DialogEvent>() {
                                    @Override
                                    public void handle(DialogEvent dialogEvent) {
                                        // đóng cửa sổ bằng bất cứ nút gì thì xóa alert khỏi map đang mở
                                        openingDialogMap.remove(alert);
                                        System.out.println(openingDialogMap.size());

                                    }
                                });

                                alert.show();

                                break;
                            }

                        } else {
                            lbError.setText("Hãy nhập đầy đủ thông tin");
                        }
                    }
                    // nếu nhấn nút cancel cũng được chương trình tính là sự kiện đóng cửa sổ dialog.close()
                    // nên nếu gọi lệnh dialog.close() thì cũng thực thi điều kiện nhấn nút cancel hoặc bất cứ nút nào khác
                    // thì thoát chương trình nếu biến xác định thay đổi ip là false hoậc chưa đăng nhập

                    // nếu là true thì nó ko thoát để cho sự kiện nhấn nút thay đổi ip có thể mở cửa sổ nhập ip mới
                    // rồi thoát khỏi vòng lặp ko hiển thị lại dialog nữa
                    else if (result.isPresent() && result.get() == ButtonType.CANCEL) {

                        if (!isIpChange) {
                            System.exit(0);
                        } else {
                            return;
                        }
                    }
                }

                // khi thoát khỏi vòng lặp tức biến đã hợp lệ thì hiển thị lại tên
                // xóa tên cũ và thêm lại tên người dùng chọn khi nhập vào
                name.setLength(0);
                name.append(tfName.getText().trim());
                lbName.setText(name.toString().toUpperCase(Locale.ROOT));

                // xóa tên cũ của namePass trong class NameDao và thêm lại tên + pass nếu check lưu mật khẩu
                // thêm tên nếu ko check lưu
                NameDao.getInstance().getNamePass().setLength(0);
                if (checkBox.isSelected()) {
                    NameDao.getInstance().getNamePass().append(user.toString2());
                } else {
                    NameDao.getInstance().getNamePass().append(user.getUsername());
                }
                // lưu lại namePass trong class NameDao vào file
                NameDao.getInstance().saveNameToFile();

                // khi đã xác định xong tên thì gửi cho server với tiêu đề xác định kiểu tin nhắn gửi tên là HEADER_NAME
                send(HEADER_NAME, user.toString2());

            }
        });
    }

    /**
     * dialog có các ô nhập vào để xác nhận
     *
     * @param title
     * @param headerText
     * @return
     */
    private Dialog<ButtonType> inputDialog(String title, String headerText) {
        // tạo dialog yêu cầu nhập tên người dùng gắn với window của  ứng dụng
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(lvContact.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.setTitle(title);
        dialog.setHeaderText(headerText);

        return dialog;
    }

    private void setCellFactory() {
        lvContact.setCellFactory(new Callback<ListView<User>, ListCell<User>>() {
            @Override
            public ListCell<User> call(ListView<User> userListView) {
                ListCell<User> listCell = new ListCell<User>() {
                    @Override
                    protected void updateItem(User user, boolean b) {
                        super.updateItem(user, b);

                        if (b) {
                            setText(null);
                            setStyle("");
                        } else {
                            setText(user.getUsername());
                            setTextFill(Color.WHITE);
                            setBorder(new Border(new BorderStroke(Color.YELLOW, BorderStrokeStyle.SOLID, CornerRadii.EMPTY,
                                    BorderWidths.DEFAULT)));

                            setFont(Font.font("Time new roman", FontWeight.BOLD, 16));

                            if (user.isConnecting()) {
                                if (isSelected()) {
                                    setStyle("-fx-background-color: blue");
                                } else {
                                    setStyle("-fx-background-color: green;");
                                }
                            } else {
                                if (isSelected()) {
                                    setStyle("-fx-background-color: blue");
                                } else {
                                    setStyle("-fx-background-color: gray;");
                                }
                            }
                        }
                    }
                };
                return listCell;
            }
        });
    }

    /**
     * khi click vào tên muốn chat cùng thì mở cửa sổ chat nếu chưa mở
     * gửi tin nhắn cần lấy lịch sử chat cho server
     *
     * @param mouseEvent
     */
    private void handleClickUser(MouseEvent mouseEvent) {

        String name = null;
        // lấy tên client đang chọn trên listview
        if (lvContact.getSelectionModel().getSelectedItem() != null) {
            name = lvContact.getSelectionModel().getSelectedItem().getUsername().trim();
        }

        // nếu tên ko null, số click là 2 và map lưu các cửa sổ đang mở ko có cửa sổ với tên trên thì mở cửa sổ
        if (name != null && mouseEvent.getClickCount() == 2 && !clientChatControllerMap.containsKey(name)) {

            openWindow(name);

        }

    }

    /**
     * mở cửa sổ liên lạc với client có tên truyền vào
     * yêu cầu server gửi lại lịch sử chat
     *
     * @param nameChat
     */
    private void openWindow(String nameChat) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    FXMLLoader loader = new FXMLLoader();
                    loader.setLocation(getClass().getResource("/ui/clientChat.fxml"));
                    Parent parent = loader.load();

                    Scene scene = new Scene(parent);
                    Stage stage = new Stage();
                    stage.setTitle("Chat with " + nameChat.toUpperCase(Locale.ROOT));
                    stage.setScene(scene);
                    stage.show();

                    // lấy controller của cửa sổ và truyền cho nó tên và đối tượng này
                    ClientChatController controller = loader.getController();
                    controller.setListContactController(nameChat, ListContactController.this);

                    // khi đóng cửa sổ này thì xóa nó khỏi map lưu các cửa sổ đang mở theo key là tên cửa sổ
                    // dù cửa sổ đã đổi tên hay chưa do user chat cùng đổi tên thì biến tên của nó cũng được cập nhật
                    // key trong map cũng đã được cập nhật sang tên mới
                    stage.setOnHiding(windowEvent -> {
                        clientChatControllerMap.remove(controller.getName().trim());
                        System.out.println(clientChatControllerMap.size());
                    });

                    // thêm cửa sổ vào map lưu các cửa sổ đang mở theo key là tên cửa sổ
                    clientChatControllerMap.put(nameChat, controller);
                    System.out.println(clientChatControllerMap.size());

                    // gửi yêu cầu gửi lịch sử chat với client này cho server
                    send(HEADER_CHAT_HISTORY, nameChat);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    /**
     * đọc kết quả từ server gửi về
     */
    private void read() {
        try {
            // đọc tin nhắn trả về từ server
            while (true) {
                String line = this.dis.readUTF();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // nếu tin nhắn trả về có header ở đầu là HEADER_USERS tức tin nhắn trả về list các users
                        // thì gọi hàm hiển thị các users này
                        if (line.startsWith(HEADER_USERS)) {
                            loadUsers(line);
                        }

                        // tin nhắn trả về lịch sử chat
                        if (line.startsWith(HEADER_CHAT_HISTORY)) {
                            sendChatHistory(line);
                        }

                        // tin nhắn nhận được sms từ user khác gửi thông qua server
                        if (line.startsWith(HEADER_SEND_FROM)) {
                            receiveSMS(line);
                        }

                        // tin nhắn tín hiệu có tài khoản user này đã bị đăng nhập nơi khác
                        if (line.startsWith(HEADER_REPLACE_LOGIN)) {
                            reportReplaceLogin();
                        }

                        // tin nhắn nhận được tên mới và cũ của user vừa đổi tên
                        if (line.startsWith(HEADER_USER_RENAME)) {
                            renameWindowChat(line);
                        }
                    }
                }).start();
            }
        } catch (IOException e) {
            // mất kết nối cho socket về null luôn
            socket = null;
            System.out.println("read error");

            // nếu là lỗi do đóng kết nối cũ thì ko làm gì rồi cho biến xác nhận đó về false do sự kiện đã xong
            // để xử lý các sự kiện lỗi do kết nối mới
            // sau đó mới gọi hàm kết nối mới, chứ nếu gọi hàm kết nối mới sớm quá nó có thể tạo socket kết nối
            // xong rồi thì mới xử lý đoạn lỗi của kết nối cũ này lại gán lại socket mới = null làm ẩn vùng nhớ
            // của nó và khi kết nối với máy chử khác nó sẽ ko thể đóng làm máy chủ cũ vẫn kết nối ngầm với user này
            if (closeOldSocket) {
                closeOldSocket = false;
                connect();
            } else {
                // khi mất kết nối với máy chủ thì in ra thông báo và thoát
                connectError();
                e.printStackTrace();
            }

        }
    }

    /**
     * đổi tên cửa sổ chat với user khác khi server gửi tên cũ và mới của user khác đó
     *
     * @param line
     */
    private void renameWindowChat(String line) {
        // tách header
        String[] strings = line.split("/./");
        // lấy phần chứa tên cũ và mới
        String oldNewName = strings[1];
        // tách tên cũ và mới cho vào mảng
        String[] arrOldNewName = oldNewName.split(":");

        // gán tên cũ và mới vào biến
        String oldName = arrOldNewName[0].trim();
        String newName = arrOldNewName[1].trim();

        // nếu map các cửa sổ chat đang mở có chứa key tên cũ của user thì đổi key đó sang tên mới
        // lấy ra controller và lấy ra nhãn chứa tên cửa sổ chính là tên của user nó chat cùng
        // và đổi tên nhãn từ tên cũ sang tên mới
        if (clientChatControllerMap.containsKey(oldName)) {

            // lấy ra controller của cửa sổ bằng key cũ
            ClientChatController controller = clientChatControllerMap.get(oldName);
            // xóa key cũ, thêm key mới vào với cùng controller
            clientChatControllerMap.remove(oldName);
            clientChatControllerMap.put(newName, controller);

            // đổi tên nhãn của cửa sổ sang tên mới
            // và cả tên của controller để khi gửi tin nhắn nó gửi lại cho đúng người nhận mới đổi tên
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    Label windowName = controller.getLbNameConnect();
                    windowName.setText("Chat with " + newName.toUpperCase(Locale.ROOT));

                    controller.setName(newName);
                }
            });
        }

    }

    /**
     * tín hiệu bị đăng nhập nơi khác server ngắt kết nối với máy này
     * set biến xác nhận là true để xử lý lỗi ngắt kết nối xác nhận cách xử lý
     */
    private void reportReplaceLogin() {
        loginReplace = true;
    }

    /**
     * xử lý nhận được tin nhắn từ server do client khác gửi cho server và server gửi lại
     *
     * @param line
     */
    private void receiveSMS(String line) {
        String[] strings = line.split("/./");
        String smsFrom = strings[1];
        String sms = strings[2];

        // nếu trong list controller đang mở có mở cửa sổ liên lạc với client đã gửi tin
        // thì lấy controller của nó ra và add tin nhắn vào cửa sổ đó để nó thêm vào list view
        // nếu chưa có thì gọi hàm mở cửa sổ liên hệ với client đã gửi tin đến, cửa sổ sẽ mở và
        // gửi tin yêu cầu server trả về lịch sử chat đã
        // add tin nhắn gửi đến vào trước nên ko cần thêm tin nhắn vào listview
        if (clientChatControllerMap.containsKey(smsFrom)) {
            ClientChatController controller = clientChatControllerMap.get(smsFrom);
            controller.addContent(sms);
        } else {
            openWindow(smsFrom);
        }
    }

    /**
     * gửi lịch sử chat cho cửa sổ chat đang mở sau khi cửa sổ này gọi yêu cầu server gửi lại lịch sử
     *
     * @param line
     */
    private void sendChatHistory(String line) {

        // tách đoạn tin nhắn server gửi về thành các phần thông tin riêng biệt
        String[] strings = line.split("/./");
        // lấy tên client muốn chat cùng ở index 1
        String chatWith = strings[1];

        // lấy controller của cửa sổ đang mở, chắc chắn có vì chỉ mở cửa sổ mới yêu cầu server gửi lại lịch sử
        ClientChatController controller = clientChatControllerMap.get(chatWith);

        if (controller != null) {
            // lấy nội dung tin nhắn bắt đầu từ đoạn index 2, gọi hàm thêm đoạn tin nhắn vào list hiển thị khung chat
            // của cửa sổ chat
            for (int i = 2; i < strings.length; i++) {
                controller.addContent2(strings[i]);
            }

            refreshListviewAfterAddChatHistory(strings, controller);

        }

    }

    /**
     * refresh nhiều lần listview trong controller của cửa sổ chat để nó hiển thị đúng vị trí cuối cùng
     *
     * @param strings
     * @param controller
     */
    private void refreshListviewAfterAddChatHistory(String[] strings, ClientChatController controller) {

        // tạo luồng refresh nhiều lần listview của controller cửa sổ chat để nó hiển thị đúng vị trí cuối cùng
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 2; i < strings.length / 10; i++) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            controller.getLvContent().scrollTo(strings.length);
                        }
                    });
                    controller.getLvContent().refresh();
                }
            }
        }).start();
    }

    /**
     * thêm các users do server gửi về vào list userlList
     *
     * @param users
     */
    private void loadUsers(String users) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                String[] userArray = users.split("/./");

                userlList.clear();

                for (int i = 1; i < userArray.length; i++) {
                    String[] userArr = userArray[i].trim().split(":");

                    String name = userArr[0].trim();
                    String pass = userArr[1].trim();
                    boolean connecting = Boolean.parseBoolean(userArr[2]);

                    User user = new User(name, pass, connecting);
                    userlList.add(user);
                }

                // làm mới lại list view để nó hiển thị đúng
                lvContact.refresh();
            }
        });
    }

    // gửi thông tin lên server có header ở đầu để xác định loại tin
    public void send(String header, String line) {
        try {
            this.dos.writeUTF(header + "/./" + line);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * hiển thị danh sách user có tên chứa đoạn tìm kiếm trên ô tìm kiếm
     *
     * @param keyEvent
     */
    public void handleSearch(KeyEvent keyEvent) {
        predicate = new Predicate<User>() {
            @Override
            public boolean test(User user) {
                if (user.getUsername().equalsIgnoreCase(name.toString())) {
                    return false;
                } else return user.getUsername().toLowerCase(Locale.ROOT)
                        .contains(tfSearch.getText().toLowerCase(Locale.ROOT));
            }
        };

        filteredList.setPredicate(predicate);
    }

    /**
     * cho biến xác định logout là true
     * đóng kết nối với máy chủ để máy chủ xóa tên kết nối cũ khỏi map các kết nối
     * khi xử lý ngoại lệ mất kết nối nó sẽ kiểm tra
     * biến logout là true thì tự gọi hàm tạo kết nối mới và hàm đăng nhập lại login
     * nếu là false thì thoát chương trình
     * <p>
     * tạo kết nối mới mà chưa đăng nhập thì máy chủ chưa xác định được tên mà chỉ dùng tên tạm
     * để tránh trường hợp đăng nhập lại với tên cũ máy chủ nghĩ là đăng nhập chèn
     *
     * @param event
     * @throws IOException
     */
    public void handleLogout(ActionEvent event) throws IOException {
        //cho biến xác định logout là true
        isLogout = true;

        // đóng kết nối với máy chủ
        socket.close();
    }

    /**
     * sự kiện đổi tên
     *
     * @param event
     */
    public void handleRename(ActionEvent event) {
        // tạo dialog yêu cầu nhập thông tin, gắn với window của ứng dụng
        Dialog<ButtonType> dialog = inputDialog("Đổi tên", "Nhập tên mới(không chứa _ hoặc .txt)");

        // tạo các control để người dùng nhập thông tin
        TextField tfNewName = new TextField();
        tfNewName.setPromptText("Enter new name(not contain _ or .txt)");
        Label lbError = new Label();
        lbError.setTextFill(Color.RED);

        // thêm các control vào vbox
        VBox vBox = new VBox(tfNewName, lbError);
        vBox.setSpacing(5);
        VBox.setMargin(vBox, new Insets(15));// set Margin
        // thêm vBox trên vào dialog
        dialog.getDialogPane().setContent(vBox);

        String oldName = name.toString();
        String newName;

        // lặp đến khi các điều kiện đúng và gọi lệnh thoát
        while (true) {

            // thêm vào map dialog đang mở này
            openingDialogMap.put(dialog, dialog);
            System.out.println(openingDialogMap.size());

            Optional<ButtonType> result = dialog.showAndWait();

            // đóng cửa sổ bằng bất cứ nút gì thì xóa dialog khỏi map đang mở
            openingDialogMap.remove(dialog);
            System.out.println(openingDialogMap.size());
            // nếu đang là sự kiện lỗi kết nối thì ko làm gì
            if (isOpenErrorAlert) {
                return;
            }

            // nếu ấn nút ok
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // lấy thông tin người dùng nhập vào
                newName = tfNewName.getText();

                // nếu chứa kí tự _ hoặc .txt thì nhảy vòng lặp để yêu cầu nhập lại
                if (newName.contains("_") || newName.contains(".txt")) {
                    lbError.setText("Tên không được chứa kí tự _ hoặc .txt");
                    continue;
                }

                // nếu 1 trong các ô nhập rỗng thì yêu cầu nhập đủ thông tin và nhảy sang vòng lặp mới
                // để show lại dialog
                if (newName.trim().isEmpty()) {
                    lbError.setText("Hãy nhập đầy đủ thông tin");
                    continue;
                }

                // xem trong list các user có user nào trùng mới tên nhập vào ko
                // nếu có lưu vào biến nhớ là true
                boolean check = false;
                for (User user : userlList) {
                    if (user.getUsername().equalsIgnoreCase(newName)) {
                        lbError.setText("Tên đã tồn tại, hãy nhập tên khác");
                        check = true;
                        break;
                    }
                }

                // nếu biến nhớ vẫn false tức là tên ko trùng trong list thì
                // thoát vòng lặp để
                // xử lý lưu tên mới ở client này và gửi cho server xử lý
                if (!check) {
                    break;
                }

            }
            // nếu nhấn nút cancel thì thoát khỏi hàm đổi tên này
            else {

                return;
            }

        }

        // nếu đã ra khỏi vòng lặp thì tên mới đã được chấp nhận
        // đổi lại tên trong đối tượng Use và tên của class này
        user.setUsername(newName);
        name.setLength(0);
        name.append(newName);

        // lấy đối tượng lưu tên và pass
        // lấy giá trị của nó ra xem nó lưu tên hay lưu cả tên và pass
        StringBuilder namePass = NameDao.getInstance().getNamePass();
        String[] namePassArr = namePass.toString().split(":");
        // clear giá trị của đối tượng này
        namePass.setLength(0);
        // nếu nó lưu chỉ tên thì ghi lại tên mới vào nó
        // nếu lưu cả tên và pass thì ghi đè tên mới và pass vào nó
        if (namePassArr.length == 1) {
            namePass.append(newName);
        } else if (namePassArr.length > 1) {
            namePass.append(user.toString2());
        }

        // lưu đối tượng vừa cập nhật tên ở trên vào file
        NameDao.getInstance().saveNameToFile();

        // hiển thị lại tên theo tên mới
        lbName.setText(newName.toUpperCase(Locale.ROOT));

        // gửi tên mới cho server để nó cập nhật
        send(HEADER_RENAME, newName);

        // hiển thị alert thông báo đổi tên thành công
        Alert alert = alertFc("Đổi tên", "Đổi tên thành công\nĐã đổi từ tên " + oldName
                + " sang " + newName, false);

        // thêm vào map alert đang mở này
        openingDialogMap.put(alert, alert);
        System.out.println(openingDialogMap.size());

        alert.setOnHiding(new EventHandler<DialogEvent>() {
            @Override
            public void handle(DialogEvent dialogEvent) {
                // đóng cửa sổ bằng bất cứ nút gì thì xóa alert khỏi map đang mở
                openingDialogMap.remove(alert);
                System.out.println(openingDialogMap.size());

            }
        });

        alert.show();

        openingDialogMap.put(alert, alert);
        System.out.println(openingDialogMap.size());
    }

    /**
     * sự kiện thay đổi mật khẩu
     *
     * @param event
     */
    public void handleChangePassword(ActionEvent event) throws IOException {
        // tạo dialog yêu cầu nhập thông tin, gắn với window của ứng dụng
        Dialog<ButtonType> dialog = inputDialog("Đổi mật khẩu", "Nhập các thông tin");

        // tạo các control để người dùng nhập thông tin
        Label lbOldPass = new Label("Mật khẩu hiện tại:");
        PasswordField pfOldPass = new PasswordField();
        pfOldPass.setPromptText("Enter current password");
        Label lbNewPass = new Label("Mật khẩu mới");
        PasswordField pfNewPass = new PasswordField();
        pfNewPass.setPromptText("Enter new password");
        Label lbReNewPass = new Label("Nhập lại Mật khẩu mới");
        PasswordField pfReNewPass = new PasswordField();
        pfReNewPass.setPromptText("Re-enter new password");
        Label lbError = new Label();
        lbError.setTextFill(Color.RED);

        CheckBox checkBox = new CheckBox("Save new password?");
        checkBox.setSelected(true);

        // thêm các control vào vbox
        VBox vBox = new VBox(lbOldPass, pfOldPass, lbNewPass, pfNewPass, lbReNewPass, pfReNewPass, checkBox, lbError);
        vBox.setSpacing(5);
        VBox.setMargin(vBox, new Insets(15));// set Margin
        // thêm vBox trên vào dialog
        dialog.getDialogPane().setContent(vBox);

        String oldPass;
        String newPass = "";
        String reNewPass;

        // lặp đến khi các điều kiện đúng và gọi lệnh thoát
        while (true) {
            // thêm vào map dialog đang mở này
            openingDialogMap.put(dialog, dialog);
            System.out.println(openingDialogMap.size());

            Optional<ButtonType> result = dialog.showAndWait();

            // đóng cửa sổ bằng bất cứ nút gì thì xóa dialog khỏi map đang mở
            openingDialogMap.remove(dialog);
            System.out.println(openingDialogMap.size());
            // nếu đang là sự kiện lỗi kết nối thì ko làm gì
            if (isOpenErrorAlert) {
                return;
            }

            // nếu ấn nút ok
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // lấy thông tin người dùng nhập vào
                oldPass = pfOldPass.getText();
                newPass = pfNewPass.getText();
                reNewPass = pfReNewPass.getText();

                // nếu 1 trong các ô nhập rỗng thì yêu cầu nhập đủ thông tin và nhảy sang vòng lặp mới
                // để show lại dialog
                if (oldPass.trim().isEmpty() || newPass.trim().isEmpty() || reNewPass.trim().isEmpty()) {
                    lbError.setText("Hãy nhập đầy đủ thông tin");
                    continue;
                }

                // nếu mật khẩu hiện tại sai cũng nhảy sang vòng lặp mới yêu cầu nhập lại
                if (!oldPass.equals(user.getPassword())) {
                    lbError.setText("Mật khẩu hiện tại không đúng");
                    continue;
                }

                // nếu các điều kiện trên đúng thì ko bị nhảy vòng lặp
                // và chạy đoạn lệnh này
                // nếu 2 pass mới ko trùng thì ko thoát vòng lặp, vòng lặp tiếp báo 2 mật khẩu ko khớp
                if (!newPass.equals(reNewPass)) {
                    lbError.setText("Hai mật khẩu mới không khớp");
                }
                // nếu 2 pass trùng thì thoát vòng lặp
                else {
                    break;
                }
            }
            // nếu nhấn nút cancel thì thoát khỏi hàm đổi pass này
            else {
                return;
            }
        }

        // nếu các điều kiện đã đúng hoặc ko nhấn nút hủy thì vòng lặp trên mới thoát và chuyển sang đoạn code dưới
        // đổi lại mật khẩu của user
        user.setPassword(newPass);
        // clear giá trị của biến lưu name pass
        NameDao.getInstance().getNamePass().setLength(0);

        // nếu check vào ô lưu mật khẩu thì thêm vào biến lưu cả name và pass
        // nếu ko check thì chỉ lưu vào biến chỉ tên
        if (checkBox.isSelected()) {
            NameDao.getInstance().getNamePass().append(user.toString2());
        } else {
            NameDao.getInstance().getNamePass().append(user.getUsername());
        }

        // ghi lại biến lưu vào file
        NameDao.getInstance().saveNameToFile();

        // gửi lại cho server sự kiện đổi pass và gửi luôn tên user đổi pass và pass đã đổi
        send(HEADER_CHANGE_PASSWORD, user.toString2());

        // gọi alert thông báo đổi pass thành công và đăng nhập lại khi tắt thông báo
        Alert alert = alertFc("Đổi mật khẩu", "Đổi mật khẩu thành công\nĐăng nhập lại để tiếp tục"
                , false);

        // thêm vào map alert đang mở này
        openingDialogMap.put(alert, alert);
        System.out.println(openingDialogMap.size());

        alert.setOnHiding(new EventHandler<DialogEvent>() {
            @Override
            public void handle(DialogEvent dialogEvent) {
                // đóng thì xóa dialog khỏi map đang mở
                openingDialogMap.remove(alert);
                System.out.println(openingDialogMap.size());
                // nếu đang là sự kiện lỗi kết nối thì ko làm gì
                if (isOpenErrorAlert) {
                    return;
                }

                // gọi hàm logout để thoát tài khoản đăng nhập lại
                try {
                    handleLogout(null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        alert.show();


    }

    /**
     * sự kiện đổi IP máy chủ
     * cho biến xác nhận sự kiện là true
     * đóng socket kết nối để xử lý ngoại lệ mất kết nối
     * ngoại lệ xem có phải là sự kiện đổi ip ko thì mở cửa sổ yêu cầu nhập lại ip
     *
     * @param event
     */
    public void handleChangeServerIp(ActionEvent event) {
//        try {
        ipInput();
//            socket.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }
}
