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
    private static final Gson gson = new Gson();

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();

        // 1) Parse filters from JSON body
        MarketFilter filter = gson.fromJson(req.getReader(), MarketFilter.class);

        // 2) Derive paging
        int offset = filter.page * filter.size;

        // 3) Start building the SQL
        StringBuilder sql = new StringBuilder(
                "SELECT listingID, playerID AS sellerID, itemCode, price, postTime " +
                        "FROM Market " +
                        "WHERE status='ACTIVE'"
        );
        List<Object> args = new ArrayList<>();

        // 4) Apply optional filters
        if (filter.itemCode != null) {
            sql.append(" AND itemCode = ?");
            args.add(filter.itemCode);
        }
        if (filter.minPrice != null) {
            sql.append(" AND price >= ?");
            args.add(filter.minPrice);
        }
        if (filter.maxPrice != null) {
            sql.append(" AND price <= ?");
            args.add(filter.maxPrice);
        }

        // 5) Sorting
        switch (filter.sortBy) {
            case "priceAsc":
                sql.append(" ORDER BY price ASC, postTime DESC");
                break;
            case "priceDesc":
                sql.append(" ORDER BY price DESC");
                break;
            default:  // "newest"
                sql.append(" ORDER BY postTime DESC");
        }

        // 6) Paging
        sql.append(" LIMIT ? OFFSET ?");
        args.add(filter.size);
        args.add(offset);

        // 7) Run the query
        try (Connection c = DataSourceSingleton.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {

            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }

            ResultSet rs = ps.executeQuery();
            List<Map<String,Object>> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(Map.of(
                        "listingID", rs.getLong("listingID"),
                        "sellerID",  rs.getInt("sellerID"),
                        "itemCode",  rs.getString("itemCode"),
                        "price",     rs.getLong("price"),
                        "postTime",  rs.getTimestamp("postTime").toInstant()
                ));
            }

            w.write(gson.toJson(rows));
        }
    }
}
