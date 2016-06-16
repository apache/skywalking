package com.ai.cloud.skywalking.plugin.jdbc;

import com.ai.cloud.skywalking.conf.AuthDesc;
import org.apache.logging.log4j.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

public class TracingDriver implements Driver {
    private static org.apache.logging.log4j.Logger logger = LogManager.getLogger(TracingDriver.class);

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

    public java.sql.Connection connect(String url, Properties info)
            throws SQLException {
        Driver driver = DriverChooser.choose(convertConnectURLIfNecessary(url));
        if (driver == null) {
            throw new SQLException("Failed to choose driver by url[{}].", convertConnectURLIfNecessary(url));
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
        Driver driver = DriverChooser.choose(convertConnectURLIfNecessary(url));
        if (driver == null) {
            return false;
        }

        return driver.acceptsURL(convertConnectURLIfNecessary(url));
    }

    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
            throws SQLException {
        return DriverChooser.choose(convertConnectURLIfNecessary(url)).
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

    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

    static class DriverChooser {
        private static org.apache.logging.log4j.Logger logger = LogManager.getLogger(DriverChooser.class);

        private static Map<String, String> urlDriverMapping = new HashMap<String, String>();

        static {
            fetchUrlDriverMapping();
        }

        public static Driver choose(String url) {
            String driverClassStr = chooseDriverClass(url);
            Driver driver = null;
            try {
                Class<?> driverClass = Class.forName(driverClassStr);
                driver = (Driver) driverClass.newInstance();
            } catch (Exception e) {
                logger.error("Failed to initial Driver class {}.", driverClassStr, e);
            }

            return driver;
        }

        private static String chooseDriverClass(String url) {
            Iterator<String> mapping = urlDriverMapping.keySet().iterator();
            while (mapping.hasNext()) {
                String urlPrefix = mapping.next();
                if (url.startsWith(urlPrefix)) {
                    String driverClassStr = urlDriverMapping.get(urlPrefix);
                    logger.debug("Success choose the driver class [" + driverClassStr + "] by connection url[ " + url + " ]");
                    return driverClassStr;
                }
            }

            logger.warn("Cannot match the driver class by connection url [" + url + "].");
            return null;
        }

        private static void fetchUrlDriverMapping() {
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(DriverChooser.class.
                        getResourceAsStream("/driver-mapping-url.def")));
                String mappingString = null;
                while ((mappingString = bufferedReader.readLine()) != null) {
                    fillMapping(mappingString);
                }
            } catch (Exception e) {
                logger.error("Failed to load driver-mapping-url.def.");
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException e) {
                        logger.error("Failed to close driver-mapping-url.def.", e);
                    }
                }
            }
        }

        private static void fillMapping(String mappingString) {
            String[] tmpMapping = mappingString.split("=");
            if (tmpMapping.length == 2) {
                urlDriverMapping.put(tmpMapping[0], tmpMapping[1]);
            }
        }
    }
}
