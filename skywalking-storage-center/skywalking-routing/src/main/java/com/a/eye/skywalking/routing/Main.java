package com.a.eye.skywalking.routing;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.logging.api.LogResolver;
import com.a.eye.skywalking.logging.impl.log4j2.Log4j2Resolver;
import com.a.eye.skywalking.network.Server;
import com.a.eye.skywalking.routing.config.Config;
import com.a.eye.skywalking.routing.config.ConfigInitializer;
import com.a.eye.skywalking.routing.listener.SpanStorageListenerImpl;
import com.a.eye.skywalking.routing.router.RoutingService;
import com.a.eye.skywalking.routing.storage.listener.NotifyListenerImpl;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class Main {

    private static final ILog logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            initConfig();
            LogManager.setLogResolver(new Log4j2Resolver());

            new NotifyListenerImpl(Config.StorageNode.SUBSCRIBE_PATH, RoutingService.getRouter());
            Server.newBuilder(Config.Routing.PORT).addSpanStorageService(new SpanStorageListenerImpl()).build().start();
            logger.info("Skywalking routing service was started.");
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Failed to start routing service.", e);
        }finally {
            RoutingService.stop();
        }
    }

    private static void initConfig() throws IllegalAccessException, IOException {
        Properties properties = new Properties();
        try {
            properties.load(Main.class.getResourceAsStream("/config.properties"));
            printStorageConfig(properties);
            ConfigInitializer.initialize(properties, Config.class);
        } catch (IllegalAccessException e) {
            logger.error("Initialize server configuration failure.", e);
            throw e;
        } catch (IOException e) {
            logger.error("Initialize server configuration failure.", e);
            throw e;
        }
    }

    private static void printStorageConfig(Properties config) {
        for (Map.Entry<Object, Object> entry : config.entrySet()) {
            logger.info("{} = {}", entry.getKey(), entry.getValue());
        }
    }
}
