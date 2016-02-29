package com.ai.cloud.skywalking.conf;

import com.ai.cloud.skywalking.selfexamination.SDKHealthCollector;

public class AuthDesc {
    static boolean isAuth = false;

    static {
        ConfigInitializer.initialize();
        ConfigValidator.validate();
        
        SDKHealthCollector.init();
    }

    public static boolean isAuth() {
        return isAuth;
    }
}
