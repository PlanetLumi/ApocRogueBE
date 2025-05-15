package apocRogueBE.BaseFunctions;

import apocRogueBE.Security.AuthHelper;
import apocRogueBE.SingletonConnection.DataSourceSingleton;
import apocRogueBE.Weapons.WeaponIDDecoder;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GetMaxLevel {
     private static final Gson gson = new Gson();

     public void service(HttpRequest req, HttpResponse resp) throws IOException, SQLException {
            resp.setContentType("application/json");

            // wrap the entire DB interaction in try-with-resources so Connection is always closed
            try (Connection conn = DataSourceSingleton.getConnection();
                 BufferedWriter w = resp.getWriter())
            {
                final int playerId;
                try {
                    playerId = AuthHelper.requirePlayerId(req, conn);
                } catch (Exception e) {
                    resp.setStatusCode(401);
                    w.write(gson.toJson(Map.of("error","Unauthorized")));
                    return;
                }

                // 2) Query inventory
                int skull = 0;
                String sql = "SELECT playerMS FROM Player WHERE playerID=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, playerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            skull = rs.getInt("playerMS");
                        }
                    }

                } catch (SQLException e) {
                    // pool timeout – tell client to retry
                    resp.setStatusCode(503);
                    w.write(gson.toJson(Map.of("error","Database busy, please retry")));
                }
                try {
                    w.write(skull);
                } catch(Exception e) {
                w.write("Skull level not found!");
                resp.setStatusCode(500);
            }
        }
    }


}
