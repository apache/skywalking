package com.ai.cloud.skywalking.plugin.jdbc.driver;

import java.io.InputStream;
import java.sql.Driver;
import java.util.Map;
import java.util.Properties;

public class DriverChooser {

    private static Properties urlDriverMapping = new Properties();

    static {
        InputStream inputStream = DriverChooser.class.getResourceAsStream("/conurl-driver-mapping.properties");
        try {
            urlDriverMapping.load(inputStream);
        } catch (Exception e) {
            System.err.println("Failed to load conurl-driver-mapping.properties");
        }
    }

    public static Driver choose(String url) throws ClassNotFoundException,
            IllegalAccessException,
            InstantiationException {
        Driver driver = null;
        for (Map.Entry<Object, Object> entry : urlDriverMapping.entrySet()) {
            if (url.startsWith(entry.getValue().toString())) {
                Class driverClass = Class.forName(entry.getKey().toString());
                driver = (Driver) driverClass.newInstance();
            }
        }
        return driver;
    }
}
