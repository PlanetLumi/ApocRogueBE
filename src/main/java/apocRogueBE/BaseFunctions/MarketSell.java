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
        System.out.println("CHECK");
        BufferedWriter w = resp.getWriter();
        SellRequest body = gson.fromJson(req.getReader(), SellRequest.class);

        try (Connection c = DataSourceSingleton.getConnection()) {
            c.setAutoCommit(false);
            System.out.println("PASSED CONNNECTION");
            // 1) find playerID from token
            int sellerId = AuthHelper.requirePlayerId(req, c);
            System.out.println("PASSED SELLER ID: " + sellerId);
            // 2) check inventory: must have at least one
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT quantity FROM Inventory WHERE playerID=? AND itemCode=? FOR UPDATE")) {
                ps.setInt(1, sellerId);
                ps.setString(2, body.itemCode);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getInt("quantity") < 1) {
                        resp.setStatusCode(400);
                        w.write("{\"error\":\"Not enough items to sell\"}");
                        return;
                    }
                    System.out.println("Passed decrement" + ps);
                } catch (Exception e){
                    System.out.println(e.getMessage());
                }
            }
            System.out.println("PASSED ITEM SELL");
            if (body.price <= 0) {
                resp.setStatusCode(400);
                w.write(gson.toJson(Map.of("error","Price must be positive")));
                return;
            }
            System.out.println("PASSED 0 CHECK");
            // 3) decrement inventory
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE Inventory SET quantity=quantity-1 WHERE playerID=? AND itemCode=?")) {
                ps.setInt(1, sellerId);
                ps.setString(2, body.itemCode);
                System.out.println("Passed decrement" + ps);
                ps.executeUpdate();
            }

            try {
                int skull = (fromHex(body.itemCode.charAt(4)));
                System.out.println("PASSED SKULL CHECK" + skull);
                // 4) insert into Market
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO Market(playerID,itemCode,postTime,price, itemSkull) VALUES (?,?,NOW(),?, ?)",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, sellerId);
                    ps.setString(2, body.itemCode);
                    ps.setLong(3, body.price);
                    ps.setInt(4, skull);
                    System.out.println("Passed market" + ps);
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

            } catch (Exception e) {
                System.out.println(e.getMessage());
            }

    }
}
