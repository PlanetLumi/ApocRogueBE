package apocRogueBE;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class DataSourceSingleton {
    private static final HikariDataSource ds;

    static {
        HikariConfig config = new HikariConfig();

        // These env-vars will be set when you deploy your function
        String instance = System.getenv("INSTANCE_CONNECTION_NAME");
        String dbName   = System.getenv("DB_NAME");
        String user     = System.getenv("DB_USER");
        String pass     = System.getenv("DB_PASS");

        // build the JDBC URL for the Cloud SQL SocketFactory
        String jdbcUrl = String.format(
                "jdbc:mysql:///%s?cloudSqlInstance=%s&socketFactory=com.google.cloud.sql.mysql.SocketFactory",
                dbName, instance
        );
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);

        // pool settings
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
