package com.ai.cloud.skywalking.plugin.jdbc;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;

import java.sql.*;
import java.util.Enumeration;
import java.util.Properties;

public class TracingDriver implements Driver {
    private static Logger logger = LogManager.getLogger(TracingDriver.class);

    private static final String TRACING_SIGN = "tracing:";

    public static final void registerDriver() {
        try {
            DriverManager.registerDriver(new TracingDriver());
        } catch (SQLException e) {
            logger.error("register TracingDriver failure.", e);
        }
    }

    private String convertConnectURLIfNecessary(String url) throws SQLException {
        if (url.toLowerCase().startsWith(TRACING_SIGN)) {
            return url.substring(TRACING_SIGN.length());
        } else {
            return url;
        }
    }

    public java.sql.Connection connect(String url, Properties info) throws SQLException {
        Driver driver = chooseActualDriver(convertConnectURLIfNecessary(url));
        if (driver == null) {
            throw new SQLException("Failed to chooseActualDriver driver by url[{}].", convertConnectURLIfNecessary(url));
        }

        java.sql.Connection conn = driver.
                connect(convertConnectURLIfNecessary(url), info);

        if (!AuthDesc.isAuth()) {
            return conn;
        } else {
            return new SWConnection(convertConnectURLIfNecessary(url), info, conn);
        }
    }

    public boolean acceptsURL(String url) throws SQLException {
        Driver driver = chooseActualDriver(convertConnectURLIfNecessary(url));
        if (driver == null) {
            return false;
        }

        return driver.acceptsURL(convertConnectURLIfNecessary(url));
    }

    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return chooseActualDriver(convertConnectURLIfNecessary(url)).
                getPropertyInfo(convertConnectURLIfNecessary(url), info);
    }

    public int getMajorVersion() {
        return 1;
    }

    public int getMinorVersion() {
        return 0;
    }

    public boolean jdbcCompliant() {
        return false;
    }

    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

    private static Driver chooseActualDriver(String url) throws SQLException {
        try {
            Driver result = null;
            for (Enumeration<Driver> drivers = DriverManager.getDrivers(); drivers.hasMoreElements(); ) {
                Driver driver = drivers.nextElement();
                if (driver instanceof TracingDriver) {
                    continue;
                }

                if (driver.acceptsURL(url)) {
                    result = driver;
                }

            }
            return result;
        } catch (Exception e) {
            logger.error("Failed to chooseActualDriver sql driver with url[" + url + "]", e);
            throw new SQLException("Failed to chooseActualDriver sql driver with url[" + url + "]", e);
        }

    }

}
