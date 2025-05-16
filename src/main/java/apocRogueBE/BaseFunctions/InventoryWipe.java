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
     * Represents each inventory item to wipe.
     * Mirrors client payload; itemCode is passed verbatim.
     */
    static class ItemEntry {
        String itemCode;
    }

    /**
     * Expect JSON: { "inventory": [ { itemCode:..., count:... }, ... ] }
     */
    static class WipeRequest {
        List<ItemEntry> inventory;
    }

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter writer = resp.getWriter();

        // Parse and validate
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

        // Authenticate and get DB connection
        try (Connection conn = DataSourceSingleton.getConnection()) {
            final int playerId;
            try {
                playerId = AuthHelper.requirePlayerId(req, conn);
            } catch (Exception authEx) {
                resp.setStatusCode(401);
                writer.write(gson.toJson(Map.of("error", "Unauthorized")));
                return;
            }

            conn.setAutoCommit(false);

            String updateSql = "UPDATE Inventory SET quantity = quantity - 1"
                    + " WHERE playerID = ? AND itemCode = ? AND quantity > 1";
            String deleteSql = "DELETE FROM Inventory"
                    + " WHERE playerID = ? AND itemCode = ? AND quantity <= 1";

            try (PreparedStatement updatePs = conn.prepareStatement(updateSql);
                 PreparedStatement deletePs = conn.prepareStatement(deleteSql)) {
                for (ItemEntry item : payload.inventory) {
                    // First try to decrement
                    updatePs.setInt(1, playerId);
                    updatePs.setString(2, item.itemCode);
                    int updated = updatePs.executeUpdate();

                    if (updated == 0) {
                        deletePs.setInt(1, playerId);
                        deletePs.setString(2, item.itemCode);
                        deletePs.executeUpdate();
                    }
                }
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
        }
    }
}
