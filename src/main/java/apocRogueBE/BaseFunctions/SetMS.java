package apocRogueBE.BaseFunctions;

import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

public class SetMS implements HttpFunction {
    private static final Gson gson = new Gson();

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws IOException {
        resp.setContentType("application/json");

        try (Connection conn = DataSourceSingleton.getConnection();
             BufferedWriter writer = resp.getWriter()) {

            // Authenticate and get player ID
            final int playerId;
            try {
                playerId = AuthHelper.requirePlayerId(req, conn);
            } catch (Exception e) {
                // 401 Unauthorized
                resp.setStatusCode(401);
                writer.write(gson.toJson(Map.of("error", "Unauthorized")));
                return;
            }

            // Parse the new skull (playerMS) value from query or body
            Long newMs = null;
            // Try query parameter first
            if (req.getFirstQueryParameter("playerMS").isPresent()) {
                try {
                    newMs = Long.parseLong(req.getFirstQueryParameter("playerMS").get());
                } catch (NumberFormatException nfe) {
                    // 400 Bad Request
                    resp.setStatusCode(400);
                    writer.write(gson.toJson(Map.of("error", "Invalid playerMS value")));
                    return;
                }
            } else {
                // Alternatively read from body
                try {
                    String body = new String(req.getInputStream().readAllBytes());
                    Map<?, ?> data = gson.fromJson(body, Map.class);
                    if (data.containsKey("playerMS")) {
                        newMs = ((Number) data.get("playerMS")).longValue();
                    }
                } catch (Exception e) {
                    // ignore, will handle null below
                }
            }

            if (newMs == null) {
                // 400 Bad Request
                resp.setStatusCode(400);
                writer.write(gson.toJson(Map.of("error", "Missing playerMS parameter")));
                return;
            }

            // Update the player's playerMS
            String updateSql = "UPDATE Player SET playerMS = ? WHERE playerID = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setLong(1, newMs);
                ps.setInt(2, playerId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    // 404 Not Found
                    resp.setStatusCode(404);
                    writer.write(gson.toJson(Map.of("error", "Player not found")));
                } else {
                    // 200 OK
                    resp.setStatusCode(200);
                    writer.write(gson.toJson(Map.of("playerID", playerId, "playerMS", newMs)));
                }
            } catch (SQLException e) {
                // 503 Service Unavailable
                resp.setStatusCode(503);
                writer.write(gson.toJson(Map.of("error", "Database error, please retry")));
            }

        } catch (SQLException e) {
            throw new IOException("Unable to obtain database connection", e);
        }
    }
}
