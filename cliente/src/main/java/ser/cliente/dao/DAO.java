package ser.cliente.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import ser.cliente.infrastructure.FilesUtils;
import ser.cliente.infrastructure.LogUtils;

public final class DAO {
    private static final String JDBC_URL = "jdbc:h2:./data/clientdb";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private static volatile boolean initialized;

    private DAO() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        String ddl = """
                CREATE TABLE IF NOT EXISTS sent_documents (
                    id IDENTITY PRIMARY KEY,
                    doc_type VARCHAR(16) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    extension VARCHAR(20),
                    size_mb DOUBLE,
                    owner VARCHAR(120) NOT NULL,
                    content CLOB,
                    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
            initialized = true;
            LogUtils.info("H2Database inicializada");
        } catch (SQLException ex) {
            throw new IllegalStateException("No fue posible inicializar H2Database", ex);
        }
    }

    public static void saveSentMessage(String content, String owner) {
        String sql = "INSERT INTO sent_documents(doc_type, name, extension, size_mb, owner, content) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "MESSAGE");
            statement.setString(2, "mensaje");
            statement.setString(3, "txt");
            statement.setDouble(4, 0.0);
            statement.setString(5, owner);
            statement.setString(6, content);
            statement.executeUpdate();
        } catch (SQLException ex) {
            LogUtils.error("No fue posible guardar el mensaje enviado", ex);
        }
    }

    public static void saveSentFile(File file, String owner) {
        String sql = "INSERT INTO sent_documents(doc_type, name, extension, size_mb, owner, content) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "FILE");
            statement.setString(2, file.getName());
            statement.setString(3, FilesUtils.extension(file.getName()));
            statement.setDouble(4, FilesUtils.toMb(file.length()));
            statement.setString(5, owner);
            statement.setString(6, null);
            statement.executeUpdate();
        } catch (SQLException ex) {
            LogUtils.error("No fue posible guardar el archivo enviado", ex);
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
    }
}