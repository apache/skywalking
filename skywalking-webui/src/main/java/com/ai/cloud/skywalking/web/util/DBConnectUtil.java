package com.ai.cloud.skywalking.web.util;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.SQLException;

@Repository
public class DBConnectUtil {


    private static Logger logger = LogManager.getLogger(DBConnectUtil.class);

    @Autowired
    private static HikariDataSource hikariDataSource;

    public Connection getConnection() {

        try {
            return hikariDataSource.getConnection();
        } catch (SQLException e) {
            logger.error("Failed to get connection", e);
            throw new RuntimeException("Cannot get connection.");
        }

    }
}
