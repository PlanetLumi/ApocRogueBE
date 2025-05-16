package apocRogueBE.BaseFunctions;

import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class SendMoney {
    private static final Gson gson = new Gson();
    public void service(HttpRequest req, HttpResponse resp) throws IOException, SQLException {
        resp.setContentType("application/json");

        // 1) Open connection + writer
        try (Connection conn = DataSourceSingleton.getConnection();
             BufferedWriter w = resp.getWriter()) {

            // 2) Authenticate
            final int playerId;
            try {
                playerId = AuthHelper.requirePlayerId(req, conn);
            } catch (AuthHelper.AuthException e) {
                resp.setStatusCode(401);
                w.write(gson.toJson(Map.of("error", "Unauthorized")));
                return;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // 3) Increment coins
            String upd = "UPDATE Player SET playerCoin = playerCoin + 1000 WHERE playerID = ?";
            try (PreparedStatement ps = conn.prepareStatement(upd)) {
                ps.setInt(1, playerId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    resp.setStatusCode(404);
                    w.write(gson.toJson(Map.of("error", "Player not found")));
                    return;
                }
            } catch (SQLException e) {
                resp.setStatusCode(503);
                w.write(gson.toJson(Map.of("error", "Database busy, please retry")));
                return;
            }

            // 4) Read back the new total (optional, but often useful)
            long newTotal;
            String qry = "SELECT playerCoin FROM Player WHERE playerID = ?";
            try (PreparedStatement ps2 = conn.prepareStatement(qry)) {
                ps2.setInt(1, playerId);
                try (ResultSet rs = ps2.executeQuery()) {
                    if (!rs.next()) {
                        resp.setStatusCode(500);
                        w.write(gson.toJson(Map.of("error", "Failed to retrieve new coin total")));
                        return;
                    }
                    newTotal = rs.getLong("playerCoin");
                }
            }

            // 5) Return success + new total
            w.write(gson.toJson(Map.of(
                    "added", 1000,
                    "newTotal", newTotal
            )));
        }
    }
    }