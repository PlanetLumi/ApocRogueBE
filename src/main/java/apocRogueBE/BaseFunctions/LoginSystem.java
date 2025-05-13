// LoginSystem.java
package apocRogueBE.BaseFunctions;

import apocRogueBE.Security.PasswordUtils;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class LoginSystem implements HttpFunction {
    private static final Gson gson = new Gson();
    private static final SecureRandom rnd = new SecureRandom();
    private static final int     TTL_MINUTES = 30;

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();
        UserCredentials cred = gson.fromJson(req.getReader(), UserCredentials.class);

        // … validation omitted for brevity …

        String sql = "SELECT playerID, password FROM UserCredentials WHERE username = ?";
        try (Connection c = DataSourceSingleton.getConnection();
             PreparedStatement selectStmt = c.prepareStatement(sql)) {

            // 1) Lookup user ID and hash
            selectStmt.setString(1, cred.getUsername());
            int playerId;
            String storedHash;
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (!rs.next()) {
                    resp.setStatusCode(401);
                    w.write("{\"authenticated\":false}");
                    return 0;
                }
                playerId  = rs.getInt("playerID");
                storedHash = rs.getString("password");
            }

            // 2) Verify password
            boolean ok = PasswordUtils.verify(cred.getPassword(), storedHash);
            if (!ok) {
                resp.setStatusCode(401);
                w.write("{\"authenticated\":false}");
                return 0;
            }

            // 3) Generate a session token
            byte[] bytes = new byte[32];
            rnd.nextBytes(bytes);
            String token = BaseEncoding.base16().lowerCase().encode(bytes);
            // you can also use new Timestamp(System.currentTimeMillis() + …) if Timestamp.from(...) gives you trouble
            Timestamp expires = Timestamp.from(
                    Instant.now().plus(Duration.ofMinutes(TTL_MINUTES)));

            // 4) Insert into Session (note the new variable name insertStmt)
            String insertSql =
                    "INSERT INTO Session(token,playerID,expires_at) VALUES (?,?,?)";
            try (PreparedStatement insertStmt = c.prepareStatement(insertSql)) {
                insertStmt.setString(1, token);
                insertStmt.setInt(2, playerId);
                insertStmt.setTimestamp(3, expires);
                insertStmt.executeUpdate();
            }

            // 5) Return the token
            resp.setStatusCode(200);
            w.write(gson.toJson(Map.of(
                    "authenticated", true,
                    "token",         token,
                    "expiresIn",     TTL_MINUTES * 60
            )));

        } catch (SQLException e) {
            resp.setStatusCode(500);
            w.write("{\"error\":\"" +
                    e.getMessage().replace("\"","\\\"") +
                    "\"}");
        }
        return 0;
    }
}
