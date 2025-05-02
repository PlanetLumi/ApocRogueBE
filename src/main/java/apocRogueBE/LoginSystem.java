// src/main/java/apocRogueBE/LoginSystem.java
package apocRogueBE;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * HTTP POST /login
 *   { "username": "...", "password": "..." }
 *
 *  → 200 + {"authenticated":true} or 401 + {"authenticated":false}
 */
public class LoginSystem implements HttpFunction {
    private static final Gson gson = new Gson();

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        BufferedWriter w = response.getWriter();
        UserCredentials cred = gson.fromJson(request.getReader(), UserCredentials.class);

        // Basic validation
        if (cred.getUsername() == null || cred.getPassword() == null) {
            response.setStatusCode(400);
            w.write("{\"error\":\"Missing username or password\"}");
            return;
        }

        String sql = "SELECT COUNT(*) FROM UserCredentials WHERE username=? AND password=?";
        try (Connection conn = DataSourceSingleton.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, cred.getUsername());
            ps.setString(2, cred.getPassword());
            try (ResultSet rs = ps.executeQuery()) {
                boolean auth = false;
                if (rs.next() && rs.getInt(1) == 1) {
                    auth = true;
                }
                response.setStatusCode(auth ? 200 : 401);
                w.write(gson.toJson(Map.of("authenticated", auth)));
            }

        } catch (SQLException e) {
            response.setStatusCode(500);
            w.write("{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }
}
