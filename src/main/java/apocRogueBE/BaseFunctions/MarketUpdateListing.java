package apocRogueBE.BaseFunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;

import java.io.BufferedWriter;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MarketUpdateListing implements HttpFunction {
    private static final Gson gson = new Gson();

    static class UpdateRequest {
        public long listingID;
        public Long newPrice;             // optional
        public Instant newExpiresAt;      // optional, if you add an expiresAt column
    }

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();
        UpdateRequest u = gson.fromJson(req.getReader(), UpdateRequest.class);

        try (Connection c = DataSourceSingleton.getConnection()) {
            c.setAutoCommit(false);

            // 1) Verify caller owns this listing
            int seller = AuthHelper.requirePlayerId(req, c);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT playerID FROM Market WHERE listingID=? FOR UPDATE")) {
                ps.setLong(1, u.listingID);
                ResultSet rs = ps.executeQuery();
                if (!rs.next() || rs.getInt("playerID") != seller) {
                    resp.setStatusCode(403);
                    w.write("{\"error\":\"Not your listing\"}");
                    return;
                }
            }

            // 2) Build dynamic UPDATE
            StringBuilder sql = new StringBuilder("UPDATE Market SET ");
            List<Object> args = new ArrayList<>();
            if (u.newPrice != null) {
                sql.append("price = ?");
                args.add(u.newPrice);
            }
            if (u.newExpiresAt != null) {
                if (!args.isEmpty()) sql.append(", ");
                sql.append("expiresAt = ?");
                args.add(Timestamp.from(u.newExpiresAt));
            }
            sql.append(" WHERE listingID = ?");
            args.add(u.listingID);

            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) {
                    ps.setObject(i+1, args.get(i));
                }
                ps.executeUpdate();
            }

            c.commit();
            w.write("{\"status\":\"UPDATED\"}");
        }
    }
}
