// File: renewShop.java
package apocRogueBE.Shop;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import apocRogueBE.SingletonConnection.DataSourceSingleton;

import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

public class renewShop implements HttpFunction {
    private static final Gson gson = new Gson();

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();

        // Clear all past‐dates so each day starts fresh.
        // Adjust "shop_date" predicate if your column differs.
        String sql = "DELETE FROM ShopPurchase WHERE shop_date < CURRENT_DATE";
        try (Connection conn = DataSourceSingleton.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int deleted = ps.executeUpdate();
            resp.setStatusCode(200);
            w.write(gson.toJson(Map.of(
                    "status",   "OK",
                    "deleted",  deleted
            )));
        } catch (Exception e) {
            resp.setStatusCode(500);
            w.write(gson.toJson(Map.of(
                    "error",    "Could not renew shop",
                    "details",  e.getMessage()
            )));
        }
    }
}
