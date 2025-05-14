package apocRogueBE.BaseFunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import apocRogueBE.Weapons.WeaponIDEncoder;

import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class InventoryWipe implements HttpFunction {
    private static final Gson gson = new Gson();

    /**
     * Represents each inventory item to wipe. Matches InventoryPush.ItemEntry structure.
     */
    static class ItemEntry {
        String typeID;
        int    skullLevel;
        int    skullSub;
        Map<String,Integer> stats;
        int    count; // count is optional here; used for consistency but not applied
    }

    /**
     * The expected JSON payload: { "inventory": [ ... ] }
     */
    static class WipeRequest {
        List<ItemEntry> inventory;
    }

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter writer = resp.getWriter();
        try (Connection conn = DataSourceSingleton.getConnection()) {
            final int playerId;
            try {
                playerId = AuthHelper.requirePlayerId(req, conn);
            } catch (Exception authEx) {
                resp.setStatusCode(401);
                writer.write(gson.toJson(Map.of("error", "Unauthorized")));
                return;
            }

        // 1) Parse and validate JSON payload
        WipeRequest payload;
        try {
            payload = gson.fromJson(req.getReader(), WipeRequest.class);
        } catch (Exception e) {
            resp.setStatusCode(400);
            writer.write(gson.toJson(Map.of("error", "Malformed JSON")));
            return;
        }
        if (payload == null || payload.inventory == null) {
            resp.setStatusCode(400);
            writer.write(gson.toJson(Map.of("error", "`inventory` field is required")));
            return;
        }

        // 2) Authenticate user and open DB connection

            conn.setAutoCommit(false);

            // 3) Prepare deletion statement
            String sql = "DELETE FROM Inventory WHERE playerID = ? AND itemCode = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (ItemEntry item : payload.inventory) {
                    // Encode server-side ID to match stored codes
                    String code = WeaponIDEncoder.encode(
                            item.typeID,
                            item.skullLevel,
                            item.skullSub,
                            item.stats
                    );
                    ps.setInt(1, playerId);
                    ps.setString(2, code);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            resp.setStatusCode(200);
            writer.write(gson.toJson(Map.of("status", "OK")));

        } catch (SQLException sqlEx) {
            resp.setStatusCode(500);
            writer.write(gson.toJson(Map.of(
                    "error", "Database error",
                    "details", sqlEx.getMessage()
            )));
        } catch (Exception ex) {
            resp.setStatusCode(500);
            writer.write(gson.toJson(Map.of("error", ex.getMessage())));
        }
    }
}
