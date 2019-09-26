package org.apache.skywalking.plugin.test.mockcollector.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {
    private static Properties config = new Properties();

    static {
        InputStream inputStream = ConfigReader.class.getClassLoader().getResourceAsStream("config.properties");
        try {
            config.load(inputStream);
        } catch (IOException e) {
            System.err.println("Failed to load config.");
            System.exit(-1);
        }
    }

    public static String getGrpcBindHost() {
        return config.getProperty("grpc_bind_host","127.0.0.1");
    }

    public static int getGrpcBindPort() {
        return Integer.parseInt(config.getProperty("grpc_bind_port","19876"));
    }
}
