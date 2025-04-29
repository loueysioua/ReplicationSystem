package database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TextRepository {
    private final int replicaId;
    private final String dbName;
    private final String baseUrl = "jdbc:mysql://localhost:3306/";
    private final String user = "root";
    private final String password = "";

    public TextRepository(int replicaId) {
        this.replicaId = replicaId;
        this.dbName = "replication" + replicaId;
        createDatabaseIfNotExists();
        createTableIfNotExists();
    }

    private void createDatabaseIfNotExists() {
        try (Connection conn = DriverManager.getConnection(baseUrl, user, password);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTableIfNotExists() {
        String url = baseUrl + dbName + "?useSSL=false&serverTimezone=UTC";
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS text_lines (
                id INT AUTO_INCREMENT PRIMARY KEY,
                line_number INT NOT NULL,
                content TEXT NOT NULL,
                timestamp BIGINT NOT NULL DEFAULT 0
            )
        """;

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTableSQL);
            
            // Check if timestamp column exists
            ResultSet columns = conn.getMetaData().getColumns(null, null, "text_lines", "timestamp");
            if (!columns.next()) {
                // Add timestamp column if it doesn't exist
                stmt.executeUpdate("ALTER TABLE text_lines ADD COLUMN timestamp BIGINT NOT NULL DEFAULT " + System.currentTimeMillis());
                // Update existing rows with current timestamp
                stmt.executeUpdate("UPDATE text_lines SET timestamp = " + System.currentTimeMillis() + " WHERE timestamp = 0");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Connection connect() throws SQLException {
        String url = baseUrl + dbName + "?useSSL=false&serverTimezone=UTC";
        return DriverManager.getConnection(url, user, password);
    }

    public void insertLine(int lineNumber, String content) {
        String sql = "INSERT INTO text_lines (line_number, content, timestamp) VALUES (?, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, lineNumber);
            stmt.setString(2, content);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public TextEntity getLastLine() {
        // Note that we're explicitly selecting all required fields including timestamp
        String sql = "SELECT line_number, content, timestamp FROM text_lines ORDER BY line_number DESC LIMIT 1";
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return new TextEntity(
                    rs.getInt("line_number"),
                    rs.getString("content"),
                    rs.getLong("timestamp")
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<TextEntity> getAllLines() {
        List<TextEntity> list = new ArrayList<>();
        String sql = "SELECT line_number, content, timestamp FROM text_lines ORDER BY line_number";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(new TextEntity(
                    rs.getInt("line_number"),
                    rs.getString("content"),
                    rs.getLong("timestamp")
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
