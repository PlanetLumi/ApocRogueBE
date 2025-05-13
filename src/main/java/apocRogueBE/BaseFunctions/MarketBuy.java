package apocRogueBE.BaseFunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;

import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;

import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

public class MarketBuy implements HttpFunction {
    private static final Gson gson = new Gson();

    static class BuyRequest {
        long listingID;
    }

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();
        BuyRequest body = gson.fromJson(req.getReader(), BuyRequest.class);

        try (Connection c = DataSourceSingleton.getConnection()) {
            c.setAutoCommit(false);

            // 1) who is buying?
            int buyerId = AuthHelper.requirePlayerId(req, c);

            // 2) lock the listing row
            long sellerId, price;
            String itemCode;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT playerID, itemCode, price FROM Market WHERE listingID=? FOR UPDATE")) {
                ps.setLong(1, body.listingID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        resp.setStatusCode(404);
                        w.write("{\"error\":\"Listing not found\"}");
                        return sellerId;
                    }
                    sellerId = rs.getInt("playerID");
                    itemCode = rs.getString("itemCode");
                    price    = rs.getLong("price");
                }
            }

            // 3) check buyer has enough coins
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT playerCoin FROM Player WHERE playerID=? FOR UPDATE")) {
                ps.setInt(1, buyerId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getLong("playerCoin") < price) {
                        resp.setStatusCode(400);
                        w.write("{\"error\":\"Insufficient balance\"}");
                        return sellerId;
                    }
                }
            }

            // 4) transfer coins
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE Player SET playerCoin=playerCoin-? WHERE playerID=?")) {
                ps.setLong(1, price);
                ps.setLong(2, buyerId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE Player SET playerCoin=playerCoin+? WHERE playerID=?")) {
                ps.setLong(1, price);
                ps.setLong(2, sellerId);
                ps.executeUpdate();
            }

            // 5) add to buyer inventory (upsert)
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO Inventory(playerID,itemCode,quantity) VALUES(?,?,1)\n" +
                            " ON DUPLICATE KEY UPDATE quantity=quantity+1")) {
                ps.setInt(1, buyerId);
                ps.setString(2, itemCode);
                ps.executeUpdate();
            }

            // 6) remove the listing
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM Market WHERE listingID=?")) {
                ps.setLong(1, body.listingID);
                ps.executeUpdate();
            }

            c.commit();
            w.write(gson.toJson(Map.of("status","PURCHASED")));

        } catch (Exception e) {
            resp.setStatusCode(500);
            w.write("{\"error\":\""+e.getMessage().replace("\"","\\\"")+"\"}");
        }
        return 0;
    }
}
