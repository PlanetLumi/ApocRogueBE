package apocRogueBE.Security;

import org.apache.http.HttpException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import java.sql.ResultSet;
import java.sql.Timestamp;

public class AuthHelper {
    /**
     * Reads “Authorization: Bearer <token>”, looks up Session,
     * and returns the playerId, or throws an HttpException(401).
     */
    public static int requirePlayerId(HttpRequest req, Connection c) throws Exception {
        String auth = req.getFirstHeader("Authorization").orElse("");
        if (!auth.startsWith("Bearer ")) throw new HttpException();

        String token = auth.substring(7).trim();
        String sql = "SELECT playerID, expires_at FROM Session WHERE token = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new HttpException();
                if (rs.getTimestamp("expires_at").before(new Timestamp(System.currentTimeMillis())))
                    throw new HttpException();
                return rs.getInt("playerID");
            }
        }
    }
    public class AuthException extends RuntimeException {
        private final int status;
        public AuthException(int status, String message) {
            super(message);
            this.status = status;
        }
        public int getStatus() { return status; }
    }
}