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
import java.util.*;

public class InventoryPull implements HttpFunction {
    private static final Gson gson = new Gson();

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();

        // 1) Auth
        Connection conn = DataSourceSingleton.getConnection();
        final int playerId;
        try {
            playerId = AuthHelper.requirePlayerId(req, conn);
        } catch (Exception e) {
            resp.setStatusCode(401);
            w.write(gson.toJson(Map.of("error","Unauthorized")));
            return;
        }

        // 2) Query
        List<Map<String,Object>> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT itemCode, quantity FROM Inventory WHERE playerID=?")) {
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String code = rs.getString("itemCode");
                int    cnt  = rs.getInt("quantity");
                var dec = WeaponIDDecoder.decode(code);

                Map<String,Object> item = new LinkedHashMap<>();
                item.put("itemCode",   code);
                item.put("typeID",     dec.typeID);
                item.put("skullLevel", dec.skullLevel);
                item.put("skullSub",   dec.skullSub);
                item.put("stats",      dec.stats);
                item.put("count",      cnt);
                out.add(item);
            }
        } catch (Exception e) {
            resp.setStatusCode(500);
            w.write(gson.toJson(Map.of("error", e.getMessage())));
            return;
        }

        // 3) Write once
        resp.setStatusCode(200);
        w.write(gson.toJson(out));
    }
}
