package apocRogueBE.BaseFunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import apocRogueBE.Weapons.WeaponIDDecoder;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLTransientConnectionException;
import java.util.*;

public class InventoryPull implements HttpFunction {
    private static final Gson gson = new Gson();

    @Override
    public void service(HttpRequest req, HttpResponse resp) {
        resp.setContentType("application/json");

        // wrap the entire DB interaction in try-with-resources so Connection is always closed
        try (Connection conn = DataSourceSingleton.getConnection();
             BufferedWriter w = resp.getWriter())
        {
            // Authenticate
            final int playerId;
            try {
                playerId = AuthHelper.requirePlayerId(req, conn);
            } catch (Exception e) {
                resp.setStatusCode(401);
                w.write(gson.toJson(Map.of("error","Unauthorized")));
                return;
            }

            //Query inventory
            List<Map<String,Object>> out = new ArrayList<>();
            String sql = "SELECT itemCode, quantity FROM Inventory WHERE playerID=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String code = rs.getString("itemCode");
                        int cnt     = rs.getInt("quantity");
                        var dec     = WeaponIDDecoder.decode(code);

                        Map<String,Object> item = new LinkedHashMap<>();
                        item.put("itemCode",   code);
                        item.put("typeID",     dec.typeID);
                        item.put("skullLevel", dec.skullLevel);
                        item.put("skullSub",   dec.skullSub);
                        item.put("stats",      dec.stats);
                        item.put("count",      cnt);
                        out.add(item);
                    }
                }

            } catch (SQLTransientConnectionException e) {
                resp.setStatusCode(503);
                w.write(gson.toJson(Map.of("error","Database busy, please retry")));
                return;
            } catch (Exception e) {
                // other SQL error
                resp.setStatusCode(500);
                w.write(gson.toJson(Map.of("error", e.getMessage())));
                return;
            }

            // Return success
            resp.setStatusCode(200);
            w.write(gson.toJson(out));

        } catch (SQLTransientConnectionException e) {
             resp.setStatusCode(503);
            try { resp.getWriter().write(gson.toJson(Map.of("error","Database busy, please retry"))); }
            catch (Exception ignored) {}
        } catch (Exception e) {
            resp.setStatusCode(500);
            try { resp.getWriter().write(gson.toJson(Map.of("error", e.getMessage()))); }
            catch (Exception ignored) {}
        }
    }
}
