package apocRogueBE.BaseFunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;

import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * Expects JSON body:
 * {
 *   "inventory": [ { "itemCode": "...", "count": 5 }, ... ]
 * }
 */
public class InventoryPush implements HttpFunction {
    private static final Gson gson = new Gson();

    static class ItemEntry {
        String itemCode;
        int count;
    }

    static class PushRequest {
        List<ItemEntry> inventory;
    }

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();

        // Parse request body
        PushRequest push = gson.fromJson(req.getReader(), PushRequest.class);
        if (push == null || push.inventory == null) {
            resp.setStatusCode(400);
            w.write("{\"error\":\"Invalid request body\"}");
            return;
        }

        // Authenticate
        try (Connection conn = DataSourceSingleton.getConnection()) {
            int playerId = AuthHelper.requirePlayerId(req, conn);
            conn.setAutoCommit(false);

            // Prepare upsert statement: overwrite quantity
            String sql = "INSERT INTO Inventory(playerID,itemID,quantity) VALUES (?,?,?) " +
                    "ON DUPLICATE KEY UPDATE quantity = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (ItemEntry entry : push.inventory) {
                    ps.setInt(1, playerId);
                    ps.setString(2, entry.itemCode);
                    ps.setInt(3, entry.count);
                    ps.setInt(4, entry.count);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();

            w.write(gson.toJson(List.of("status","OK")));
        } catch (Exception e) {
            resp.setStatusCode(500);
            w.write("{\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
        }
    }
}
