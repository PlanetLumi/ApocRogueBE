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
import java.util.List;
import java.util.Map;

/**
 * Expects JSON body:
 * {
 *   "inventory": [
 *     {
 *       "typeID":     "DR",           // your 2-char typeID
 *       "skullLevel": 3,
 *       "skullSub":   1,
 *       "stats":      { "damage":45, "dashSpeed":10, … },
 *       "count":      2
 *     },
 *     …
 *   ]
 * }
 */
public class InventoryPush implements HttpFunction {
    private static final Gson gson = new Gson();

    static class ItemEntry {
        String typeID;
        int    skullLevel;
        int    skullSub;
        Map<String,Integer> stats;
        int    count;
    }

    static class PushRequest {
        List<ItemEntry> inventory;
    }

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();

        // 1) parse request
        PushRequest push = gson.fromJson(req.getReader(), PushRequest.class);
        if (push == null || push.inventory == null) {
            resp.setStatusCode(400);
            w.write("{\"error\":\"Invalid request body\"}");
            return;
        }

        // 2) auth & DB
        try (Connection conn = DataSourceSingleton.getConnection()) {
            int playerId = AuthHelper.requirePlayerId(req, conn);
            conn.setAutoCommit(false);

            // 3) prepare upsert
            String sql = ""
                    + "INSERT INTO Inventory(playerID, itemID, quantity) VALUES (?,?,?) "
                    + "ON DUPLICATE KEY UPDATE quantity = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (ItemEntry e : push.inventory) {
                    // 3a) server-side encoding
                    String code = WeaponIDEncoder.encode(
                            e.typeID, e.skullLevel, e.skullSub, e.stats
                    );

                    // 3b) bind & batch
                    ps.setInt(1, playerId);
                    ps.setString(2, code);
                    ps.setInt(3, e.count);
                    ps.setInt(4, e.count);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            w.write(gson.toJson(Map.of("status","OK")));
        } catch (Exception ex) {
            resp.setStatusCode(500);
            w.write("{\"error\":\""+ex.getMessage().replace("\"","\\\"")+"\"}");
        }
    }
}
