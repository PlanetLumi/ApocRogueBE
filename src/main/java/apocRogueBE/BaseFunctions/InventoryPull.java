package apocRogueBE.BaseFunctions;
import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InventoryPull implements HttpFunction {
    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        Connection conn = DataSourceSingleton.getConnection();
        int playerId = AuthHelper.requirePlayerId(req, conn);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT itemID, quantity FROM Inventory WHERE playerID=?")) {
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> inv = new ArrayList<>();
            while (rs.next()) {
                inv.add(Map.of(
                        "itemCode", rs.getString("itemID"),
                        "count", rs.getInt("quantity")
                ));
            }
            resp.getWriter().write(new Gson().toJson(inv));
        }
    }
}