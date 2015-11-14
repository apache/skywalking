package com.ai.cloud.skywalking.buffer;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.conf.ConfigInitializer;
import com.ai.cloud.skywalking.context.Span;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContextBuffer {
    private static Logger logger = Logger.getLogger(ContextBuffer.class.getName());
    private static boolean isAuth = true;

    static {
        InputStream inputStream = ContextBuffer.class.getResourceAsStream("/sky-walking.auth");
        if (inputStream == null) {
            isAuth = false;
            logger.log(Level.ALL, "No provider sky-walking certification documents, buried point won't work");
        }
        if (isAuth) {
            try {
                Properties properties = new Properties();
                properties.load(inputStream);
                ConfigInitializer.initialize(properties, Config.class);
            } catch (IllegalAccessException e) {
                isAuth = false;
                logger.log(Level.ALL, "Parsing certification file failed, buried won't work");
            } catch (IOException e) {
                isAuth = false;
                logger.log(Level.ALL, "Failed to read the certification file, buried won't work");
            }

            ContextBuffer.init();
        }
    }

    private static BufferPool pool;

    private ContextBuffer() {
        //non
    }

    private static void init() {
        if (pool == null)
            pool = new BufferPool();
    }

    public static void save(Span span) {
        if (!isAuth)
            return;
        pool.save(span);
    }
}
