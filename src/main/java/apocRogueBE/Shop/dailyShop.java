package apocRogueBE.Shop;

import com.google.cloud.functions.*;
import com.google.gson.Gson;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import apocRogueBE.Security.AuthHelper;

import java.io.BufferedWriter;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

public class dailyShop implements HttpFunction {
    private static final Gson GSON = new Gson();
    private static final String VERSION = "v1";
    private static final String SECRET  = System.getenv("SHOP_SALT");

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        try (BufferedWriter out = resp.getWriter();
             Connection     conn = DataSourceSingleton.getConnection()) {

            int playerId = AuthHelper.requirePlayerId(req, conn);
            String sellerId = req.getFirstQueryParameter("seller")
                    .orElseThrow(() -> new IllegalArgumentException("Missing seller"));

            // deterministic RNG
            String today = LocalDate.now().toString();
            long seed = Objects.hash(playerId, sellerId, today, VERSION, SECRET);
            Random rng = new Random(seed);

            // roll base inventory
            List<ShopItem> base = ShopGenerator.generateShop(rng, sellerId, playerId);

            // fetch previously-bought quantities
            Map<String,Integer> bought = new HashMap<>();
            String sql = """
              SELECT item_code, quantity
                FROM ShopPurchase
               WHERE player_id=? AND seller_id=? AND shop_date=?""";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt   (1, playerId);
                ps.setString(2, sellerId);
                ps.setDate  (3, Date.valueOf(today));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) bought.put(rs.getString(1), rs.getInt(2));
                }
            }

            // translate to DTO
            List<ShopEntry> entries = new ArrayList<>();
            for (ShopItem it : base) {
                int remaining = Math.max(0, it.getBaseStock() - bought.getOrDefault(it.getCode(),0));
                entries.add(new ShopEntry(){
                    { itemCode = it.getCode();
                        typeID   = it.getCode().substring(2,4); // first two after "ID"/"IT"
                        price    = it.getPrice();
                        remaining= remaining; }
                });
            }

            SellerConfig.Seller cfg = SellerConfig.get(sellerId);

            io.github.apocRogue.dto.SellerInfo dto = new io.github.apocRogue.dto.SellerInfo();
            dto.sellerID     = sellerId;
            dto.displayName  = cfg.displayName();
            dto.portraitPath = "ui/portraits/" + cfg.portraitKey() + ".png";
            dto.spentGauge   = bought.values().stream().mapToInt(Integer::intValue).sum();
            dto.items        = entries;

            resp.setStatusCode(200);

            out.write(GSON.toJson(dto));
        } catch (IllegalArgumentException iae) {
            resp.setStatusCode(400);
            resp.getWriter().write(GSON.toJson(Map.of("error", iae.getMessage())));
        } catch (Exception e) {
            resp.setStatusCode(500);
            resp.getWriter().write(GSON.toJson(Map.of("error","Could not build shop",
                    "details", e.getMessage())));
        }
    }
}
