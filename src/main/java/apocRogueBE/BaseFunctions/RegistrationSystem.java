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

        try (Connection conn = DataSourceSingleton.getConnection()) {
            // use a transaction so we only commit both inserts together
            conn.setAutoCommit(false);

            // 1) ensure username is free in Player
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

            // 2) insert into Player, get generated playerID
            int playerId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO Player(username, playerMS, playerCoin) VALUES (?, 0, 0)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, cred.getUsername());
                int affected = ps.executeUpdate();
                if (affected != 1) throw new SQLException("Could not create Player row");
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No Player ID generated");
                    playerId = keys.getInt(1);
                }
            }

            // 3) insert into UserCredentials
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO UserCredentials(playerID, username, password) VALUES (?,?,?)")) {
                ps.setInt(1, playerId);
                ps.setString(2, cred.getUsername());           // if you still keep username column here
                ps.setString(3, PasswordUtils.hash(cred.getPassword()));
                int affected = ps.executeUpdate();
                if (affected != 1) throw new SQLException("Could not create credentials row");
            }

            // 4) all good—commit and return success
            conn.commit();
            response.setStatusCode(200);
            w.write("{\"registered\":true}");

        } catch (SQLException e) {
            // rollback on any SQL failure
            try { DataSourceSingleton.getConnection().rollback(); } catch(Exception ignore){}
            response.setStatusCode(500);
            w.write("{\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
        }
    }
}
