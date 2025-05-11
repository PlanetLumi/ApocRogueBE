package apocRogueBE.BaseFunctions;
import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import apocRogueBE.Weapons.WeaponIDDecoder;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import org.apache.http.HttpException;

import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static apocRogueBE.BaseFunctions.RegistrationSystem.gson;

public class InventoryPull implements HttpFunction {
    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();
        Connection conn = DataSourceSingleton.getConnection();
        int playerId;
        try {
            playerId = AuthHelper.requirePlayerId(req, conn);
        } catch (HttpException e) {
            resp.setStatusCode(401);

            return;
        }

        List<Map<String,Object>> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT itemCode, quantity FROM Inventory WHERE playerID=?")) {
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String code = rs.getString("itemCode");
                int    cnt  = rs.getInt("quantity");

                // decode the code into stats
                WeaponIDDecoder.Decoded dec = WeaponIDDecoder.decode(code);

                // build your dummy‐item object
                Map<String,Object> itemBean = new HashMap<>();
                itemBean.put("itemCode",   code);
                itemBean.put("typeID",     dec.typeID);
                itemBean.put("skullLevel", dec.skullLevel);
                itemBean.put("skullSub",   dec.skullSub);
                itemBean.put("stats",      dec.stats);      // a map of statName→value
                itemBean.put("count",      cnt);

                out.add(itemBean);
            }
        } catch (SQLException e) {
        resp.setStatusCode(500);
        resp.getWriter().write(gson.toJson(Map.of("error", e.getMessage())));
        return;
    }
        resp.setStatusCode(200);
        resp.getWriter().write(gson.toJson(out));
        w.write(gson.toJson(out));
    }

}