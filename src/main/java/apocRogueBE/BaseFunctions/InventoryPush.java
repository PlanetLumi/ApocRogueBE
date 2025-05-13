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
        PushRequest push;
        try {
            push = gson.fromJson(req.getReader(), PushRequest.class);
        } catch(Exception e) {
            resp.setStatusCode(400);
            w.write(gson.toJson(Map.of("error","Malformed JSON")));
            return;
        }
        if (push == null || push.inventory == null) {
            resp.setStatusCode(400);
            w.write(gson.toJson(Map.of("error","`inventory` field is required")));
            return;
        }

        // 2) auth & DB
        try (Connection conn = DataSourceSingleton.getConnection()) {
            final int playerId;
            try {
                playerId = AuthHelper.requirePlayerId(req, conn);
            } catch (Exception authEx) {
                // missing or invalid JWT
                resp.setStatusCode(401);
                w.write(gson.toJson(Map.of("error","Unauthorized")));
                return;
            }

            conn.setAutoCommit(false);
            String sql = ""
                    + "INSERT INTO Inventory(playerID, itemCode, quantity) VALUES (?,?,?) "
                    + "ON DUPLICATE KEY UPDATE quantity = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (ItemEntry e : push.inventory) {
                    // server-side ID encoding
                    String code = WeaponIDEncoder.encode(
                            e.typeID, e.skullLevel, e.skullSub, e.stats
                    );
                    ps.setInt(1, playerId);
                    ps.setString(2, code);
                    ps.setInt(3, e.count);
                    ps.setInt(4, e.count);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            resp.setStatusCode(200);
            w.write(gson.toJson(Map.of("status","OK")));
        } catch (SQLException sqlEx) {
            resp.setStatusCode(500);
            w.write(gson.toJson(Map.of("error","Database error", "details", sqlEx.getMessage())));
        } catch (Exception ex) {
            // any other unexpected exception
            resp.setStatusCode(500);
            w.write(gson.toJson(Map.of("error", ex.getMessage())));
        }
    }
}
