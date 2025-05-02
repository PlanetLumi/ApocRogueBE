// src/main/java/apocRogueBE/RegistrationSystem.java
package apocRogueBE.BaseFunctions;

import apocRogueBE.Security.PasswordUtils;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * HTTP POST /register
 *   { "username": "...", "password": "..." }
 *
 *  → { "registered": true } or { "error": "reason" }
 */
public class RegistrationSystem implements HttpFunction {
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

        // Insert into DB
        String sql = "INSERT INTO UserCredentials (username, password) VALUES (?,?)";
        try (Connection conn = DataSourceSingleton.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, cred.getUsername());
            String hashed = PasswordUtils.hash(cred.getPassword());
            ps.setString(2, hashed);
            int updated = ps.executeUpdate();
            if (updated == 1) {
                response.setStatusCode(200);
                w.write("{\"registered\":true}");
            } else {
                response.setStatusCode(500);
                w.write("{\"registered\":false}");
            }

        } catch (SQLException e) {
            response.setStatusCode(500);
            // In production, avoid returning raw SQL messages
            w.write("{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }
}
