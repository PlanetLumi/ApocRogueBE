package apocRogueBE.WorldGen;

import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class CheckBucket implements HttpFunction {
    private static final Gson GSON = new Gson();

    static class CheckRequest {
        public List<InvItem> inventory;
    }
    static class InvItem { public String itemCode; public int count; }
    static class CheckResponse { public boolean passed; public String message;
        public CheckResponse(boolean p, String m) { passed = p; message = m; }
    }

    @Override public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter writer = resp.getWriter();

        // parse and auth
        CheckRequest cr = GSON.fromJson(req.getReader(), CheckRequest.class);
        int playerId;
        try (Connection c = DataSourceSingleton.getConnection()) {
            playerId = AuthHelper.requirePlayerId(req, c);
        }
        if (cr == null || cr.inventory == null) {
            resp.setStatusCode(400);
            writer.write(GSON.toJson(new CheckResponse(false, "Invalid request payload")));
            return;
        }

        // load generated loot from SQL
        Map<String,Integer> genCounts = new HashMap<>();
        try (Connection conn = DataSourceSingleton.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT item_code FROM world_loot WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    genCounts.merge(rs.getString("item_code"), 1, Integer::sum);
                }
            }
        }

        if (genCounts.isEmpty()) {
            resp.setStatusCode(404);
            writer.write(GSON.toJson(new CheckResponse(false, "No generated loot found for player")));
            return;
        }

        // tally inventory counts
        Map<String,Integer> invCounts = new HashMap<>();
        for (InvItem it : cr.inventory) {
            invCounts.put(it.itemCode, it.count);
        }

        // compare
        for (Map.Entry<String,Integer> e : genCounts.entrySet()) {
            int have = invCounts.getOrDefault(e.getKey(), 0);
            if (have < e.getValue()) {
                writer.write(GSON.toJson(new CheckResponse(false,
                        String.format("Missing %d of %s (have %d)", e.getValue(), e.getKey(), have)
                )));
                return;
            }
        }

        // passed: delete rows for this player
        try (Connection conn = DataSourceSingleton.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM world_loot WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            ps.executeUpdate();
        }

        writer.write(GSON.toJson(new CheckResponse(true, "Inventory matches generation; log cleared")));
    }
}
