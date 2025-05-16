package apocRogueBE.SingletonConnection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class DataSourceSingleton {
    private static final HikariDataSource ds;

    static {
        HikariConfig config = new HikariConfig();

        String dbName   = System.getenv("DB_NAME");
        String user     = System.getenv("DB_USER");
        String pass     = System.getenv("DB_PASS");
        String instance = System.getenv("INSTANCE_CONNECTION_NAME");

        String jdbcUrl =
                String.format(
                        "jdbc:mysql:///%s" +
                                "?socketFactory=com.google.cloud.sql.mysql.SocketFactory" +
                                "&cloudSqlInstance=%s" +
                                "&useSSL=false",
                        dbName, instance
                );

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        System.out.println("🛠 DataSourceSingleton:");
        System.out.println("    DB_NAME              = " + dbName);
        System.out.println("    DB_USER              = " + user);
        System.out.println("    INSTANCE_CONNECTION_NAME = " + instance);
        System.out.println("    -> JDBC URL          = " + jdbcUrl);

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
