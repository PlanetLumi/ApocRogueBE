package apocRogueBE.BaseFunctions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import apocRogueBE.SingletonConnection.DataSourceSingleton;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

public class MarketUpdate implements HttpFunction {
    private static final Gson gson = new Gson();

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();

        try (Connection c = DataSourceSingleton.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM Market\n" +
                             " WHERE postTime < DATE_SUB(NOW(),INTERVAL 2 DAY)")) {
            int deleted = ps.executeUpdate();
            w.write(gson.toJson(Map.of("deleted", deleted)));
        }
    }
}
