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
import java.io.IOException;
import java.security.spec.ECField;
import java.sql.*;
import java.time.Instant;
import java.util.*;

import static apocRogueBE.Weapons.WeaponGenerate.loadAllWeaponData;
import static apocRogueBE.Weapons.WeaponIDDecoder.decode;
import static java.lang.System.out;

public class MarketPull implements HttpFunction {
    private static final Gson GSON = new Gson();
    private static final Map<String, WeaponData> WEAPON_DATA_MAP = loadAllWeaponData();

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();

        //Parse filters
        MarketFilter filter = GSON.fromJson(req.getReader(), MarketFilter.class);
        out.println("First CALL");
        try (Connection conn = DataSourceSingleton.getConnection()) {
            //Authenticate & get player skull
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
                    out.println("Player found");
                    skull = rs.getInt("playerMS");
                    out.println("SKULL");
                }
            }

            //Build dynamic SQL
            StringBuilder sql = new StringBuilder()
                    .append("SELECT listingID, playerID AS sellerID, ")
                    .append("itemCode, price, postTime, itemSkull ")
                    .append("FROM Market ");
            out.println("String built" + sql.toString());
            //Optional filters
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
            out.println("PAST NULL BLOCKS");

            //Always sort first by skull-proximity…
            sql.append("ORDER BY ABS(itemSkull - ?) ");
            params.add(skull);

            switch (filter.sortBy) {
                case "priceAsc":
                    sql.append(", price ASC, postTime DESC ");
                    break;
                case "priceDesc":
                    sql.append(", price DESC ");
                    break;
                default:
                    sql.append(", postTime DESC ");
            }
            out.println("FILTER");

            // 7) Pagination
            sql.append("LIMIT ? OFFSET ? ");
            params.add(filter.size);
            params.add(filter.page * filter.size);

            // 8) Execute
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                try {
                    for (int i = 0; i < params.size(); i++) {

                        ps.setObject(i + 1, params.get(i));
                        out.println("Param " + (i + 1) + " : " + params.get(i));
                    }
                } catch (SQLException e) {
                    out.println(e.toString());
                    throw new RuntimeException(e);
                }
                ResultSet rs = ps.executeQuery();
                out.println(rs);
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    String code = rs.getString("itemCode");
                    long price = rs.getLong("price");
                    skull = rs.getInt("itemSkull");
                    long listingId = rs.getLong("listingID");
                    int sellerId = rs.getInt("sellerID");
                // Decode the itemCode into full stats
                    WeaponIDDecoder.Decoded decoded = WeaponIDDecoder.decode(code);
                    WeaponData wd = WEAPON_DATA_MAP.get(decoded.typeID);
                    String weaponName = wd.name;

                    //Pull out the map of all stats
                    Map<String, Integer> statsMap = decoded.stats;

                    int damage = statsMap.getOrDefault("damage", 0);
                    int dashSpeed = statsMap.getOrDefault("dashSpeed", 0);
                    int noiseLevel = statsMap.getOrDefault("noiseLevel", 0);
                    int animationSpeed = statsMap.getOrDefault("animationSpeed", 0);
                    int dashDuration = statsMap.getOrDefault("dashDuration", 0);
                    int dashCoolDown = statsMap.getOrDefault("dashCoolDown", 0);
                    Map<String, Object> row = new LinkedHashMap<>();

                    row.put("listingID", listingId);
                    row.put("sellerID", sellerId);
                    row.put("itemCode", code);
                    row.put("price", price);
                    String postTime = rs.getTimestamp("postTime").toString();
                    row.put("postTime", postTime.toString());
                    row.put("itemSkull", skull);
                    row.put("name", weaponName);
                    row.put("damage", damage);
                    row.put("speed", animationSpeed);
                    row.put("dashSpeed", dashSpeed);
                    row.put("noiseLevel", noiseLevel);
                    row.put("dashDuration", dashDuration);
                    row.put("dashCoolDown", dashCoolDown);

                    rows.add(row);
                }
                w.write(GSON.toJson(rows));
                out.println(GSON.toJson(rows));
                out.println("MADE IT");
            } catch (Exception e) {
                out.println(e.toString());
            }

        }
    }

}
