package com.ai.cloud.skywalking.alarm.util;

import com.ai.cloud.skywalking.alarm.conf.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnectUtil {

    private static Logger logger = LogManager.getLogger(DBConnectUtil.class);

    private static Connection con;

    static {
        try {
            Class.forName(Config.DB.DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            logger.error("Failed to found DB driver class.", e);
            System.exit(-1);
        }

        try {
            con = DriverManager.getConnection(Config.DB.URL, Config.DB.USER_NAME, Config.DB.PASSWORD);
        } catch (SQLException e) {
            logger.error("Failed to connect DB", e);
            System.exit(-1);
        }
    }

    public static Connection getConnection() {
        return con;
    }
}
