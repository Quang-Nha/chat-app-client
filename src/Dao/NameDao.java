package Dao;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * lưu tên user và đọc ghi tên từ file
 */
public class NameDao {
    private final StringBuilder namePass;
    private static NameDao instance;
    private final Path NAME_FILE = FileSystems.getDefault().getPath("name.txt");

    private NameDao() {
        namePass = new StringBuilder();
    }

    public synchronized static NameDao getInstance() {
        if (instance == null) {
            instance = new NameDao();
        }
        return instance;
    }

    public StringBuilder getNamePass() {
        return namePass;
    }

    /**
     * đọc tên từ file lưu vào biến name
     */
    public void readNameFromFile() {
        // tạo file nếu chưa có
        if (Files.notExists(NAME_FILE)) {
            try {
                Files.createFile(NAME_FILE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(NAME_FILE)) {

            Scanner scanner = new Scanner(reader);

            while (scanner.hasNextLine()) {
                namePass.append(scanner.nextLine());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * lưu tên từ biến name vào file
     */
    public void saveNameToFile() {
        try (BufferedWriter writer = Files.newBufferedWriter(NAME_FILE)) {
                writer.write(namePass.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



}
