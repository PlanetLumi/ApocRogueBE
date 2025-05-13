package apocRogueBE.Shop;

import apocRogueBE.Items.ItemIDDecoder;
import apocRogueBE.Items.ItemTypeInfo;
import apocRogueBE.Items.ItemTypeRegistry;
import apocRogueBE.Weapons.WeaponData;
import apocRogueBE.Weapons.WeaponIDDecoder;
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
    private static final String SECRET = System.getenv("SHOP_SALT");

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter out = resp.getWriter();             // keep writer open for whole request

        try (Connection conn = DataSourceSingleton.getConnection()) {

            /*────────────────  1 ── identify caller & seller  ────────────────*/
            int playerId = AuthHelper.requirePlayerId(req, conn);

            String sellerId = req.getFirstQueryParameter("seller")
                    .orElseThrow(() -> new IllegalArgumentException("Missing seller"));

            boolean potionSeller = "A".equalsIgnoreCase(sellerId);
            boolean weaponSeller = "C".equalsIgnoreCase(sellerId);
            // anything else ( "B" today) is a general-item seller

            /*────────────────  2 ── deterministic RNG for this day  ───────────*/
            String today = LocalDate.now().toString();
            long seed = Objects.hash(playerId, sellerId, today, VERSION, SECRET);
            Random rng = new Random(seed);

            /*────────────────  3 ── generate base shop & fetch past purchases ‐‐*/
            List<ShopItem> base = ShopGenerator.generateShop(rng, sellerId, playerId);

            Map<String, Integer> bought = new HashMap<>();
            String sql = """
          
                    SELECT item_code, quantity
            FROM ShopPurchase
           WHERE player_id=? AND seller_id=? AND shop_date=?""";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {ps.setInt   (1, playerId);
                ps.setString(2, sellerId);
                ps.setDate  (3, Date.valueOf(today));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) bought.put(rs.getString(1), rs.getInt(2));
                }
            }

            /*────────────────  4 ── build DTO entries with names & icons  ─────*/
            ItemTypeRegistry items   = ShopGenerator.loadItemRegistry();
            ItemTypeRegistry potions = ShopGenerator.loadPotionRegistry();
            Map<String,WeaponData> weapons = ShopGenerator.loadWeaponData();

            List<ShopEntry> entries = new ArrayList<>();

            for (ShopItem it : base) {
                ShopEntry e = new ShopEntry();
                e.itemCode  = it.getCode();
                if (weaponSeller) {
                    e.stats = WeaponIDDecoder.decode(e.itemCode).stats;
                } else {
                    e.stats = ItemIDDecoder.decode(e.itemCode).stats;
                }
                e.typeID    = it.getCode().substring(2,4);
                e.price     = it.getPrice();
                e.remaining = Math.max(0, it.getBaseStock()
                        - bought.getOrDefault(it.getCode(),0));

                if ("C".equalsIgnoreCase(sellerId)) {                       // weapons
                    WeaponData wd = Objects.requireNonNull(
                            weapons.get(e.typeID), "unknown weapon "+e.typeID);
                    e.name        = wd.name;
                    e.texturePath = wd.texturePath;

                } else {                                                    // items/potions
                    ItemTypeRegistry reg = "A".equalsIgnoreCase(sellerId) ? potions : items;
                    ItemTypeInfo info = Objects.requireNonNull(
                            reg.getByTypeID(e.typeID), "unknown item "+e.typeID);
                    e.name        = info.getName();
                    e.texturePath = info.getTexturePath();
                }
                entries.add(e);
            }

            /*────────────────  5 ── wrap in SellerInfo and serialize  ─────────*/
            SellerConfig.Seller cfg = SellerConfig.get(sellerId);

            io.github.apocRogue.dto.SellerInfo dto = new io.github.apocRogue.dto.SellerInfo();
            dto.sellerID     = sellerId;
            dto.displayName  = cfg.displayName();
            dto.portraitPath = "ui/portraits/" + cfg.portraitKey() + ".png";
            dto.spentGauge   = bought.values().stream().mapToInt(Integer::intValue).sum();
            dto.items        = entries;

            resp.setStatusCode(200);
            out.write(GSON.toJson(dto));

        } catch (IllegalArgumentException iae) {          // bad request
            resp.setStatusCode(400);
            out.write(GSON.toJson(Map.of("error", iae.getMessage())));

        } catch (Exception e) {                           // anything else
            resp.setStatusCode(500);
            Map<String,Object> body = new HashMap<>();
            body.put("error", "Could not build shop");
            body.put("details", Optional.ofNullable(e.getMessage())
                    .orElse(e.getClass().getName()));
            out.write(GSON.toJson(body));
            e.printStackTrace();
        }
        return 0;
    }
    }
