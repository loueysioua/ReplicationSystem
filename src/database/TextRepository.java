package database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public class TextRepository {
    private final String url;

    public TextRepository(int replicaId) {
        this.url = "jdbc:mysql://localhost:3306/replication" + replicaId + "?useSSL=false&serverTimezone=UTC";
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, "root", "");
    }

    public void insertLine(int lineNumber, String content) {
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO text_lines (line_number, content) VALUES (?, ?)")) {
            stmt.setInt(1, lineNumber);
            stmt.setString(2, content);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public JSONObject getLastLineAsJson() {
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT line_number, content FROM text_lines ORDER BY line_number DESC LIMIT 1")) {
            if (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("line_number", rs.getInt("line_number"));
                obj.put("content", rs.getString("content"));
                return obj;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<JSONObject> getAllLinesAsJson() {
        List<JSONObject> list = new ArrayList<>();
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT line_number, content FROM text_lines ORDER BY line_number")) {
            while (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("line_number", rs.getInt("line_number"));
                obj.put("content", rs.getString("content"));
                list.add(obj);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
