package apocRogueBE.BaseFunctions;

import apocRogueBE.SingletonConnection.DataSourceSingleton;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import java.sql.Connection;

public class HealthCheck implements HttpFunction {
    @Override
    public void service(HttpRequest req, HttpResponse resp) throws Exception {
        resp.appendHeader("Content-Type", "application/json");
        try (Connection c = DataSourceSingleton.getConnection()) {
            c.createStatement().execute("SELECT 1");
            resp.setStatusCode(200);
            resp.getWriter().write("{\"status\":\"ok\"}");
        } catch (Exception e) {
            resp.setStatusCode(500);
            resp.getWriter().write("{\"status\":\"error\",\"msg\":\"" + e.getMessage() + "\"}");
        }
        return 0;
    }
}