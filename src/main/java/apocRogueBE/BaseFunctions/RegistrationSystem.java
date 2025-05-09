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
import java.sql.ResultSet;
import java.sql.SQLException;

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
        UserCredentials cred = gson.fromJson(request.getReader(), UserCredentials.class);

        // Basic validation
        if (cred.getUsername() == null || cred.getPassword() == null) {
            response.setStatusCode(400);
            w.write("{\"error\":\"Missing username or password\"}");
            return;
        }
        String check = PasswordUtils.securityPrints(validatePassword(cred.getPassword(), cred.getUsername()));
        if(!(check.equals("0"))){
            response.setStatusCode(401);
            w.write(check);
            return;
        }

        // 1) Grab a Connection resource
        try (Connection conn = DataSourceSingleton.getConnection()) {

            // 2) Check for existing user
            String checkSql = "SELECT 1 FROM UserCredentials WHERE username = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, cred.getUsername());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        // user already exists
                        response.setStatusCode(409);
                        w.write("{\"error\":\"Username already taken\"}");
                        return;
                    }
                }
            }

            // 3) Insert new user
            String insertSql = "INSERT INTO UserCredentials(username,password) VALUES (?,?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, cred.getUsername());
                String hashed = PasswordUtils.hash(cred.getPassword());
                insertStmt.setString(2, hashed);
                int updated = insertStmt.executeUpdate();
                response.setStatusCode(updated == 1 ? 200 : 500);
                w.write(updated == 1
                        ? "{\"registered\":true}"
                        : "{\"registered\":false}");
            }

        } catch (SQLException e) {
            // Handle any unexpected SQL errors
            response.setStatusCode(500);
            w.write("{\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
        }
    }

}
