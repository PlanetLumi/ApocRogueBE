package apocRogueBE.BaseFunctions;

import apocRogueBE.Weapons.WeaponData;
import apocRogueBE.Weapons.WeaponIDDecoder;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;

import java.io.BufferedWriter;
import java.sql.*;
import java.time.Instant;
import java.util.*;

import static apocRogueBE.Weapons.WeaponGenerate.loadAllWeaponData;
import static apocRogueBE.Weapons.WeaponIDDecoder.decode;

public class MarketPull implements HttpFunction {
        private static final Gson GSON = new Gson();
    private static final Map<String, WeaponData> WEAPON_DATA_MAP = loadAllWeaponData();
        @Override
        public void service(HttpRequest req, HttpResponse resp) throws Exception {
            resp.setContentType("application/json");
            BufferedWriter w = resp.getWriter();

            // 1) Parse filters
            MarketFilter filter = GSON.fromJson(req.getReader(), MarketFilter.class);
            System.out.println("First CALL");
            try (Connection conn = DataSourceSingleton.getConnection()) {
                // 2) Authenticate & get player skull
                int playerId = AuthHelper.requirePlayerId(req, conn);
                int skull;
                try (PreparedStatement psSkull = conn.prepareStatement(
                        "SELECT playerMS FROM Player WHERE playerID = ?")) {
                    psSkull.setInt(1, playerId);
                    try (ResultSet rs = psSkull.executeQuery()) {
                        if (!rs.next()) {
                            resp.setStatusCode(404);
                            w.write(GSON.toJson(Map.of("error", "Player not found")));
                            return;
                        }
                        System.out.println("Player found");
                        skull = rs.getInt("playerMS");
                        System.out.println("SKULL");
                    }
                }

                // 3) Build dynamic SQL
                StringBuilder sql = new StringBuilder()
                        .append("SELECT listingID, playerID AS sellerID, ")
                        .append("itemCode, price, postTime, itemSkull ")
                        .append("FROM Market ")
                        .append("WHERE status = 'ACTIVE' ");
                System.out.println("String built" + sql.toString());
                // 4) Optional filters
                List<Object> params = new ArrayList<>();
                if (filter.itemCode != null) {
                    sql.append("AND itemCode = ? ");
                    params.add(filter.itemCode);
                }
                if (filter.minPrice != null) {
                    sql.append("AND price >= ? ");
                    params.add(filter.minPrice);
                }
                if (filter.maxPrice != null) {
                    sql.append("AND price <= ? ");
                    params.add(filter.maxPrice);
                }
                System.out.println("PAST NULL BLOCKS");

                // 5) Always sort first by skull-proximity…
                sql.append("ORDER BY ABS(itemSkull - ?) ");
                params.add(skull);

                // 6) …then by whichever secondary sort the user chose
                switch (filter.sortBy) {
                    case "priceAsc":
                        sql.append(", price ASC, postTime DESC ");
                        break;
                    case "priceDesc":
                        sql.append(", price DESC ");
                        break;
                    default: // newest
                        sql.append(", postTime DESC ");
                }
                System.out.println("FILTER");

                // 7) Pagination
                sql.append("LIMIT ? OFFSET ? ");
                params.add(filter.size);
                params.add(filter.page * filter.size);

                // 8) Execute
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {

                        ps.setObject(i + 1, params.get(i));
                        System.out.println("Param " + (i + 1) + " : " + params.get(i));
                    }
                    ResultSet rs = ps.executeQuery();
                    System.out.println(rs);
                    List<Map<String,Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        String code      = rs.getString("itemCode");
                        long   price     = rs.getLong("price");
                        skull     = rs.getInt("itemSkull");
                        long   listingId = rs.getLong("listingID");
                        int    sellerId  = rs.getInt("sellerID");
                        Instant postTime = rs.getTimestamp("postTime").toInstant();

                        // Decode the itemCode into full stats
                        WeaponIDDecoder.Decoded decoded = WeaponIDDecoder.decode(code);
                        WeaponData wd = WEAPON_DATA_MAP.get(decoded.typeID);
                        String weaponName = wd.name;

                        // 2) Pull out the map of all stats
                        Map<String,Integer> statsMap = decoded.stats;

                        // Now you can do, for example:
                        int damage    = statsMap.getOrDefault("damage", 0);
                        int dashSpeed = statsMap.getOrDefault("dashSpeed", 0);
                        int noiseLevel = statsMap.getOrDefault("noiseLevel", 0);
                        int animationSpeed = statsMap.getOrDefault("animationSpeed", 0);
                        int dashDuration = statsMap.getOrDefault("dashDuration", 0);
                        int dashCoolDown = statsMap.getOrDefault("dashCoolDown", 0);
                        Map<String,Object> out = new LinkedHashMap<>();
                        out.put("listingID",   listingId);
                        out.put("sellerID",    sellerId);
                        out.put("itemCode",    code);
                        out.put("price",       price);
                        out.put("postTime",    postTime);
                        out.put("itemSkull",   skull);
                        out.put("name",        weaponName);
                        out.put("damage",      damage);
                        out.put("speed",       animationSpeed);
                        out.put("dashSpeed",   dashSpeed);
                        out.put("noiseLevel",  noiseLevel);
                        out.put("dashDuration",dashDuration);
                        out.put("dashCoolDown",dashCoolDown);

                        rows.add(out);
                    }
                    w.write(GSON.toJson(rows));
                    System.out.println(GSON.toJson(rows));
                    System.out.println("MADE IT");
                }


            } catch (AuthHelper.AuthException e) {
                resp.setStatusCode(401);
                w.write(GSON.toJson(Map.of("error", "Unauthorized")));
            } catch (SQLException e) {
                resp.setStatusCode(503);
                w.write(GSON.toJson(Map.of("error", "Database error, please retry")));
            }
        }

}
