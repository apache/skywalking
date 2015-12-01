package com.ai.cloud.skywalking.conf;

public class AuthDesc {
    static boolean isAuth = false;

    static {
        ConfigInitializer.initialize();
        ConfigValidator.validate();
    }

    public static boolean isAuth() {
        return isAuth;
    }
}
