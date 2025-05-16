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

import static apocRogueBE.Weapons.WeaponIDDecoder.fromHex;

public class MarketSell implements HttpFunction {
    private static final Gson gson = new Gson();

    static class SellRequest {
        String itemCode;
        long price;      // in cents
    }

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();
        SellRequest body = gson.fromJson(req.getReader(), SellRequest.class);

        try (Connection c = DataSourceSingleton.getConnection()) {
            c.setAutoCommit(false);

            // 1) find playerID from token
            int sellerId = AuthHelper.requirePlayerId(req, c);

            // 2) check inventory: must have at least one
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT quantity FROM Inventory WHERE playerID=? AND itemID=? FOR UPDATE")) {
                ps.setInt(1, sellerId);
                ps.setString(2, body.itemCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getInt("quantity") < 1) {
                        resp.setStatusCode(400);
                        w.write("{\"error\":\"Not enough items to sell\"}");
                        return;
                    }
                }
            }

            // 3) decrement inventory
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE Inventory SET quantity=quantity-1 WHERE playerID=? AND itemID=?")) {
                ps.setInt(1, sellerId);
                ps.setString(2, body.itemCode);
                ps.executeUpdate();
            }
            int skull = (fromHex(body.itemCode.charAt(4)));
            // 4) insert into Market
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO Market(playerID,itemCode,postTime,price, itemSkull) VALUES (?,?,NOW(),?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, sellerId);
                ps.setString(2, body.itemCode);
                ps.setLong(3, body.price);
                ps.setInt(4, skull);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    long listingId = keys.getLong(1);
                    c.commit();
                    w.write(gson.toJson(
                            Map.of("listingID", listingId,
                                    "status",    "LISTED")));
                    return;
                }
            }

        } catch (Exception e) {
            resp.setStatusCode(500);
            w.write("{\"error\":\""+e.getMessage().replace("\"","\\\"")+"\"}");
        }
    }
}
