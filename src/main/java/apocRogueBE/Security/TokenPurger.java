package apocRogueBE.Security;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import apocRogueBE.SingletonConnection.DataSourceSingleton;

import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class TokenPurger implements HttpFunction {
    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        // delete all expired sessions
        try (Connection c = DataSourceSingleton.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM Session WHERE expires_at < NOW()")) {
            int deleted = ps.executeUpdate();
            resp.getWriter().write("{\"deleted\":" + deleted + "}");
        }
        return 0;
    }
}