package entity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Server {
    public static String hostIp = "";
    public static final int POST = 1234;
    private static final Path DIR_NAME = Paths.get(".", "server");
    private static final Path FILE_NAME = Paths.get(DIR_NAME.toAbsolutePath().toString(), "server_IP.txt");

    // đoạn code chạy đầu tiên khi mở ứng dụng
    // đọc địa chỉ ip máy chủ từ file
    static {
        try {
            if (Files.notExists(DIR_NAME)) {
                Files.createDirectory(DIR_NAME);
            }
            if (Files.notExists(FILE_NAME)) {
                Files.createFile(FILE_NAME);
            }

            BufferedReader reader = Files.newBufferedReader(FILE_NAME);

            String line = "";

            for (; (line = reader.readLine()) != null; hostIp += line) {
            }

            if (hostIp.trim().isEmpty()) {
                hostIp = "127.0.0.1";
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * ghi địa chỉ IP server vào file
     */
    public static void saveIpServerToFile() {
        try (BufferedWriter writer = Files.newBufferedWriter(FILE_NAME)) {
            writer.write(hostIp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
