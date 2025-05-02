// LoginSystem.java
package apocRogueBE;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class LoginSystem implements HttpFunction {
    private static final Gson gson = new Gson();

    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        BufferedWriter w = resp.getWriter();
        UserCredentials cred = gson.fromJson(req.getReader(), UserCredentials.class);

        if (cred==null
                || cred.getUsername()==null || cred.getUsername().isBlank()
                || cred.getPassword()==null || cred.getPassword().isBlank()) {
            resp.setStatusCode(400);
            w.write("{\"error\":\"username and password required\"}");
            return;
        }

        String sql = "SELECT COUNT(*) FROM UserCredentials WHERE username=? AND password=?";
        try (Connection c = DataSourceSingleton.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, cred.getUsername());
            ps.setString(2, cred.getPassword());
            try (ResultSet rs = ps.executeQuery()) {
                boolean ok = rs.next() && rs.getInt(1)==1;
                resp.setStatusCode(ok?200:401);
                w.write(gson.toJson(Map.of("authenticated", ok)));
            }

        } catch (SQLException e) {
            resp.setStatusCode(500);
            w.write("{\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
        }
    }
}
