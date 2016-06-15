package com.ai.cloud.skywalking.plugin.jdbc.driver;

import java.io.InputStream;
import java.sql.Driver;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DriverChooser {
	private static Logger logger = LogManager.getLogger(DriverChooser.class);

    private static Properties urlDriverMapping = new Properties();

    static {
        InputStream inputStream = DriverChooser.class.getResourceAsStream("/driver-mapping-url.properties");
        try {
            urlDriverMapping.load(inputStream);
        } catch (Exception e) {
            logger.error("Failed to load driver-mapping-url.properties");
        }
    }

    public static Driver choose(String url) throws ClassNotFoundException,
            IllegalAccessException,
            InstantiationException {
        Driver driver = null;
        for (Map.Entry<Object, Object> entry : urlDriverMapping.entrySet()) {
            if (url.startsWith(entry.getValue().toString())) {
                Class<?> driverClass = Class.forName(entry.getKey().toString());
                driver = (Driver) driverClass.newInstance();
            }
        }
        return driver;
    }
}
