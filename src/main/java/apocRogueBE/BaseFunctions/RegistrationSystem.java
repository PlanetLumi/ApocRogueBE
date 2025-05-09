// src/main/java/apocRogueBE/RegistrationSystem.java
package apocRogueBE.BaseFunctions;

import apocRogueBE.Security.PasswordUtils;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.sql.*;

import static apocRogueBE.Security.PasswordUtils.validatePassword;

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
        response.setContentType("application/json");
        UserCredentials cred = gson.fromJson(request.getReader(), UserCredentials.class);

        // 0) Basic validation
        if (cred.getUsername() == null || cred.getPassword() == null) {
            response.setStatusCode(400);
            w.write("{\"error\":\"Missing username or password\"}");
            return;
        }
        String check = PasswordUtils.securityPrints(
                validatePassword(cred.getPassword(), cred.getUsername()));
        if (!"0".equals(check)) {
            response.setStatusCode(401);
            w.write("{\"error\":\"" + check.replace("\"","\\\"") + "\"}");
            return;
        }

        // 1) Open connection & start a transaction
        try (Connection conn = DataSourceSingleton.getConnection()) {
            conn.setAutoCommit(false);

            // 2) Ensure username is free in Player
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM Player WHERE username = ?")) {
                ps.setString(1, cred.getUsername());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        response.setStatusCode(409);
                        w.write("{\"error\":\"Username already taken\"}");
                        return;
                    }
                }
            }

            // 3) Create the Player row and grab its ID
            int playerId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO Player(username, playerMS, playerCoin) VALUES (?,0,0)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, cred.getUsername());
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Failed to insert Player");
                }
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("No playerID returned");
                    }
                    playerId = keys.getInt(1);
                }
            }

            // 4) Insert credentials tied to that playerID
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO UserCredentials(playerID,username,password) VALUES (?,?,?)")) {
                ps.setInt(1, playerId);
                ps.setString(2, cred.getUsername());
                ps.setString(3, PasswordUtils.hash(cred.getPassword()));
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Failed to insert credentials");
                }
            }

            // 5) Commit & respond
            conn.commit();
            response.setStatusCode(200);
            w.write("{\"registered\":true}");

        } catch (SQLException e) {
            // Rollback on any failure
            response.setStatusCode(500);
            w.write("{\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
        }
    }

}
