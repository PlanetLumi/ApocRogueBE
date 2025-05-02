package apocRogueBE.SingletonConnection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class DataSourceSingleton {
    private static final HikariDataSource ds;

    static {
        HikariConfig config = new HikariConfig();

        // pull in all three from env-vars
        String dbName   = System.getenv("DB_NAME");
        String user     = System.getenv("DB_USER");
        String pass     = System.getenv("DB_PASS");
        String instance = System.getenv("INSTANCE_CONNECTION_NAME");

        // tell the driver to use the Cloud SQL socket factory
        String jdbcUrl =
                String.format(
                        "jdbc:mysql:///%s" +
                                "?socketFactory=com.google.cloud.sql.mysql.SocketFactory" +
                                "&cloudSqlInstance=%s" +
                                "&useSSL=false",          // optional, but avoids cert headaches
                        dbName, instance
                );

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);

        // (you can tune these however you like)
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);

        ds = new HikariDataSource(config);
    }

    private DataSourceSingleton() { }

    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }
}
