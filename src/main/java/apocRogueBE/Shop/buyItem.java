// File: buyItem.java   (back-end Cloud Function)
package apocRogueBE.Shop;

import com.google.cloud.functions.*;
import com.google.gson.Gson;
import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;

import java.io.BufferedWriter;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

public class buyItem implements HttpFunction {
    private static final Gson  GSON   = new Gson();
    private static final String SECRET  = System.getenv("SHOP_SALT");
    private static final String VERSION = "v1";

    private record BuyReq(String sellerID, String itemCode, int count){}

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        try (BufferedWriter out = resp.getWriter();
             Connection conn = DataSourceSingleton.getConnection()) {

            /* 1 ── parse JSON */
            BuyReq br = GSON.fromJson(req.getReader(), BuyReq.class);
            if (br==null||br.sellerID()==null||br.itemCode()==null||br.count()<1) {
                resp.setStatusCode(400); out.write("{\"error\":\"bad json\"}"); return;
            }

            /* 2 ── auth */
            int playerId = AuthHelper.requirePlayerId(req, conn);

            /* 3 ── deterministically rebuild today's shop to know true price */
            String today = LocalDate.now().toString();
            long seed = Objects.hash(playerId, br.sellerID(), today, VERSION, SECRET);
            Random rng = new Random(seed);
            List<ShopItem> base = ShopGenerator.generateShop(rng, br.sellerID(), playerId);

            ShopItem wanted = base.stream()
                    .filter(s -> s.getCode().equals(br.itemCode()))
                    .findFirst()
                    .orElse(null);
            if (wanted == null) {
                resp.setStatusCode(400); out.write("{\"error\":\"item not in today\\'s shop\"}"); return;
            }
            int totalCost = wanted.getPrice() * br.count();

            /* 4 ── load player's gold & spent gauge */
            conn.setAutoCommit(false);

            int gold;
            String spentColumn = switch (br.sellerID()) {
                case "A" -> "amountSpentA";
                case "B" -> "amountSpentB";
                case "C" -> "amountSpentC";
                default  -> throw new IllegalArgumentException("Unknown sellerID");
            };

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT playerCoin," + spentColumn +
                            " FROM Player WHERE playerID=? FOR UPDATE")) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); resp.setStatusCode(500); return; }
                    gold = rs.getInt("playerCoin");
                }
            }
            if (gold < totalCost) {
                conn.rollback();
                resp.setStatusCode(402);
                out.write("{\"error\":\"not enough coin\"}");
                return;
            }

            /* ---------- 5 ── debit coin and add to amountSpentX ---------- */
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE Player SET playerCoin = playerCoin-?, "
                            + spentColumn + " = " + spentColumn + " + ? "
                            + "WHERE playerID=?")) {
                ps.setInt(1, totalCost);
                ps.setInt(2, totalCost);   // add full price to the seller’s spent column
                ps.setInt(3, playerId);
                ps.executeUpdate();
            }

            /* 6 ── upsert ShopPurchase (track stock) */
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO ShopPurchase(player_id,seller_id,shop_date,item_code,quantity)
                         VALUES(?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE quantity=quantity+?
                    """)) {
                ps.setInt   (1, playerId);
                ps.setString(2, br.sellerID());
                ps.setDate  (3, Date.valueOf(today));
                ps.setString(4, br.itemCode());
                ps.setInt   (5, br.count());
                ps.setInt   (6, br.count());
                ps.executeUpdate();
            }

            /* 7 ── add to Inventory */
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO Inventory(playerID,itemCode,quantity)
                         VALUES(?,?,?)
                    ON DUPLICATE KEY UPDATE quantity=quantity+?
                    """)) {
                ps.setInt   (1, playerId);
                ps.setString(2, br.itemCode());
                ps.setInt   (3, br.count());
                ps.setInt   (4, br.count());
                ps.executeUpdate();
            }

            conn.commit();
            resp.setStatusCode(200);
            out.write("{\"status\":\"OK\",\"goldLeft\":"+(gold-totalCost)+"}");
        }
    }
}
