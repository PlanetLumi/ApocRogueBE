package apocRogueBE.Security;

import apocRogueBE.SingletonConnection.DataSourceSingleton;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import static apocRogueBE.BaseFunctions.RegistrationSystem.gson;

public class ProfileSystem implements HttpFunction {
    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.setContentType("application/json");
        BufferedWriter w = resp.getWriter();

        try (Connection c = DataSourceSingleton.getConnection()) {
            // 1) Authenticate and get the playerId
            int playerId = AuthHelper.requirePlayerId((HttpRequest) req, c);

            // 2) Load that player’s profile
            PreparedStatement ps = c.prepareStatement(
                    "SELECT username, playerMS, playerCoin FROM Player WHERE playerID = ?"
            );
            ps.setInt(1, playerId);

            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                resp.setStatusCode(404);
                w.write("{\"error\":\"Player not found\"}");
                return;
            }

            // 3) Write out JSON
            Map<String,Object> profile = Map.of(
                    "playerID",   playerId,
                    "username",   rs.getString("username"),
                    "playerMS",   rs.getLong("playerMS"),
                    "playerCoin", rs.getLong("playerCoin")
            );
            w.write(gson.toJson(profile));

        } catch (AuthHelper.AuthException ae) {
            // our custom exception with getStatus()
            resp.setStatusCode(ae.getStatus());
            w.write("{\"error\":\"" + ae.getMessage() + "\"}");
        } catch (Exception e) {
            // any other error
            resp.setStatusCode(500);
            w.write("{\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
        }
    }
}
