package com.ai.cloud.skywalking.buffer;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.conf.ConfigInitializer;
import com.ai.cloud.skywalking.context.Span;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ContextBuffer {
    private static boolean isAuth = true;

    static {
        InputStream inputStream = ContextBuffer.class.getResourceAsStream("/sky-walking.auth");
        if (inputStream == null) {
            isAuth = false;
        }
        if (isAuth) {
            try {
                Properties properties = new Properties();
                properties.load(inputStream);
                ConfigInitializer.initialize(properties, Config.class);
            } catch (IllegalAccessException e) {
                isAuth = false;
            } catch (IOException e) {
                isAuth = false;
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
