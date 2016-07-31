package com.ai.cloud.skywalking.conf;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.selfexamination.SDKHealthCollector;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

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
            InputStream authFileInputStream;
            String urlString = ClassLoader.getSystemClassLoader().getResource(generateLocationPath()).toString();
            urlString = urlString.substring(urlString.indexOf("file:"), urlString.indexOf('!'));
            URL url = new URL(urlString);
            File file = new File(url.toURI());
            authFileInputStream = new FileInputStream(file.getParentFile().getName() + File.separator + "/sky-walking.auth");
            return authFileInputStream;
        } catch (Exception e) {
            logger.error("Error to fetch auth file input stream.", e);
            return null;
        }
    }

    private static String generateLocationPath() {
        return AuthDesc.class.getName().replaceAll(".", "/") + ".class";
    }

    public static boolean isAuth() {
        return isAuth;
    }
}
