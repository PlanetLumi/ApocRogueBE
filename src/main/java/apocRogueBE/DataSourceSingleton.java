package apocRogueBE;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class DataSourceSingleton {
    private static final HikariDataSource ds;

    static {
        HikariConfig config = new HikariConfig();

        // env-vars for your DB creds
        String dbName = System.getenv("DB_NAME");
        String user   = System.getenv("DB_USER");
        String pass   = System.getenv("DB_PASS");

        // point at the local Cloud SQL Auth proxy on localhost:3306
        String jdbcUrl = String.format("jdbc:mysql://127.0.0.1:3306/%s", dbName);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);

        // your pool settings
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);

        ds = new HikariDataSource(config);
    }

    private DataSourceSingleton() { /* no-op */ }

    /** Grab a connection from the pool */
    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }
}
