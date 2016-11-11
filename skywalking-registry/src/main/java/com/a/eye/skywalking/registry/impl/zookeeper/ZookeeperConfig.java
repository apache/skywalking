package com.a.eye.skywalking.registry.impl.zookeeper;

import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;

import java.util.Properties;

/**
 * Created by xin on 2016/11/10.
 */
public class ZookeeperConfig {
    private static      ILog   logger      = LogManager.getLogger(ZookeeperConfig.class);
    public static final String CONNECT_URL = "CONNECT_URL";
    public static final String AUTH_SCHEMA = "AUTH_SCHEMA";
    public static final String AUTH_INFO   = "AUTH_INFO";

    private String connectURL;
    private String autSchema;
    private byte[] auth;

    public ZookeeperConfig(Properties config) {
        this.connectURL = config.getProperty(CONNECT_URL);
        if (this.connectURL == null || this.connectURL.length() == 0) {
            throw new IllegalArgumentException("Connect url cannot be null");
        }

        this.autSchema = config.getProperty(AUTH_SCHEMA);
        String authString = config.getProperty(AUTH_INFO);
        if (authString != null) {
            this.auth = authString.getBytes();
        }
        logger.info("connection url: {} \n auth schema : {} \n auth info : {} ", connectURL, autSchema, authString);
    }

    public boolean hasAuthInfo() {
        return (this.autSchema != null && this.autSchema.length() > 0) && (this.auth != null && this.auth.length > 0);
    }

    public String getConnectURL() {
        return connectURL;
    }

    public String getAutSchema() {
        return autSchema;
    }

    public byte[] getAuth() {
        return auth;
    }
}
