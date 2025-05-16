package apocRogueBE.BaseFunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;

import java.io.BufferedWriter;
import java.sql.*;
import java.util.*;

public class MarketPull implements HttpFunction {
        private static final Gson GSON = new Gson();

        @Override
        public void service(HttpRequest req, HttpResponse resp) throws Exception {
            resp.setContentType("application/json");
            BufferedWriter w = resp.getWriter();

            // 1) Parse filters
            MarketFilter filter = GSON.fromJson(req.getReader(), MarketFilter.class);

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
                        skull = rs.getInt("playerMS");
                    }
                }

                // 3) Build dynamic SQL
                StringBuilder sql = new StringBuilder()
                        .append("SELECT listingID, playerID AS sellerID, ")
                        .append("itemCode, price, postTime, itemSkull ")
                        .append("FROM Market ")
                        .append("WHERE status = 'ACTIVE' ");

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

                // 7) Pagination
                sql.append("LIMIT ? OFFSET ? ");
                params.add(filter.size);
                params.add(filter.page * filter.size);

                // 8) Execute
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    ResultSet rs = ps.executeQuery();

                    List<Map<String,Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        rows.add(Map.of(
                                "listingID",  rs.getLong("listingID"),
                                "sellerID",   rs.getInt("sellerID"),
                                "itemCode",   rs.getString("itemCode"),
                                "price",      rs.getLong("price"),
                                "postTime",   rs.getTimestamp("postTime").toInstant(),
                                "itemSkull",  rs.getInt("itemSkull")
                        ));
                    }
                    w.write(GSON.toJson(rows));
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

}
