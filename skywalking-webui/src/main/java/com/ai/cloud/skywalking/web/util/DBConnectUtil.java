package com.ai.cloud.skywalking.web.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.SQLException;

@Repository
public class DBConnectUtil {

    @Value("#{configProperties['jdbc.url']}")
    private String dbURL;
    @Value("#{configProperties['jdbc.username']}")
    private String dbUser;
    @Value("#{configProperties['jdbc.password']}")
    private String password;
    @Value("#{configProperties['jdbc.driverClassName']}")
    private String dbDriverClass;

    private static Logger logger = LogManager.getLogger(DBConnectUtil.class);

    private static HikariDataSource hikariDataSource;

    public Connection getConnection() {
        if (hikariDataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbURL);
            config.setUsername(dbUser);
            config.setPassword(password);
            config.setDriverClassName(dbDriverClass);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariDataSource = new HikariDataSource(config);
        }
        try {
            return hikariDataSource.getConnection();
        } catch (SQLException e) {
            logger.error("Failed to get connection", e);
            throw new RuntimeException("Cannot get connection.");
        }

    }
}
