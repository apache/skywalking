package com.ai.cloud.skywalking.conf;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.selfexamination.SDKHealthCollector;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class AuthDesc {
    private static Logger  logger = LogManager.getLogger(AuthDesc.class);
    static         boolean isAuth = false;

    static {
        InputStream authFileInputStream;
        if (Config.SkyWalking.IS_PREMAIN_MODE) {
            authFileInputStream = fetchAuthFileInputStream();
        } else {
            authFileInputStream = AuthDesc.class.getResourceAsStream("/sky-walking.auth");
        }

        ConfigInitializer.initialize(authFileInputStream);
        ConfigValidator.validate();
        SDKHealthCollector.init();
    }

    private static InputStream fetchAuthFileInputStream() {
        try {
            return new FileInputStream(Config.SkyWalking.AGENT_BASE_PATH + File.separator + "/sky-walking.auth");
        } catch (Exception e) {
            logger.error("Error to fetch auth file input stream.", e);
            return null;
        }
    }

    public static boolean isAuth() {
        return isAuth;
    }
}
