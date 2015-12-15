package com.ai.cloud.skywalking.alarm.util;

import com.ai.cloud.skywalking.alarm.conf.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;

public class DBConnectUtil {

    private static Logger logger = LogManager.getLogger(DBConnectUtil.class);

    private static HikariDataSource hikariDataSource;

    public static Connection getConnection() {
        if (hikariDataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(Config.DB.URL);
            config.setUsername(Config.DB.USER_NAME);
            config.setPassword(Config.DB.PASSWORD);
            config.setDriverClassName(Config.DB.DRIVER_CLASS);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.setMinimumIdle(Config.DB.MAX_IDLE);
            config.setMaximumPoolSize(Config.DB.MAX_POOL_SIZE);
            config.setConnectionTimeout(Config.DB.CONNECT_TIMEOUT);
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
