package com.a.eye.skywalking.routing;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.logging.impl.log4j2.Log4j2Resolver;
import com.a.eye.skywalking.network.Server;
import com.a.eye.skywalking.registry.RegistryCenterFactory;
import com.a.eye.skywalking.registry.api.RegistryCenter;
import com.a.eye.skywalking.registry.assist.NetUtils;
import com.a.eye.skywalking.registry.impl.zookeeper.ZookeeperConfig;
import com.a.eye.skywalking.routing.config.Config;
import com.a.eye.skywalking.routing.http.RestfulAPIService;
import com.a.eye.skywalking.routing.listener.SpanStorageListenerImpl;
import com.a.eye.skywalking.routing.listener.TraceSearchListenerImpl;
import com.a.eye.skywalking.routing.router.RoutingService;
import com.a.eye.skywalking.util.ConfigInitializer;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static com.a.eye.skywalking.registry.assist.NetUtils.isAnyHost;

/**
 * Main Class of the routing server.
 * It starts server in a sequence:
 * 1. init config
 * 2. init logManager
 * 3. start restful service
 * 4. registry server
 * 5. open service, and start listening port.
 *
 * @author wusheng
 */
public class Main {

    private static final ILog logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        RestfulAPIService restfulAPIService = null;
        try {
            initConfig();
            LogManager.setLogResolver(new Log4j2Resolver());
            restfulAPIService = new RestfulAPIService(Config.Server.REST_SERVICE_HOST,Config.Server
                    .REST_SERVICE_PORT);
            restfulAPIService.doStart();
            RegistryCenter center = RegistryCenterFactory.INSTANCE.getRegistryCenter(Config.RegistryCenter.TYPE);
            center.start(fetchRegistryCenterConfig());
            center.subscribe(Config.StorageNode.SUBSCRIBE_PATH, RoutingService.getRouter());

            Server.newBuilder(Config.Server.HOST,Config.Server.PORT).addSpanStorageService(new
                    SpanStorageListenerImpl()).addTraceSearchService(new TraceSearchListenerImpl()).build().start();

            center.register(Config.RegistryCenter.PATH_PREFIX + fetchHostOfStorageServerBinding
                    () + ":" + Config.Server.PORT);
            logger.info("Skywalking routing service was started.");
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Failed to start routing service.", e);
            System.exit(-1);
        } finally {
            restfulAPIService.doStop();
            RoutingService.stop();
        }
    }

    private static String fetchHostOfStorageServerBinding(){
        if (isAnyHost(Config.Server.HOST)){
            return NetUtils.getLocalAddress().getHostAddress();
        }

        return Config.Server.HOST;
    }

    private static void initConfig() throws IllegalAccessException, IOException {
        Properties properties = new Properties();
        try {
            properties.load(Main.class.getResourceAsStream("/config.properties"));
            printRoutingConfig(properties);
            ConfigInitializer.initialize(properties, Config.class);
        } catch (IllegalAccessException e) {
            logger.error("Initialize server configuration failure.", e);
            throw e;
        } catch (IOException e) {
            logger.error("Initialize server configuration failure.", e);
            throw e;
        }
    }

    private static void printRoutingConfig(Properties config) {
        for (Map.Entry<Object, Object> entry : config.entrySet()) {
            logger.info("{} = {}", entry.getKey(), entry.getValue());
        }
    }

    private static Properties fetchRegistryCenterConfig() {
        Properties centerConfig = new Properties();
        centerConfig.setProperty(ZookeeperConfig.CONNECT_URL, Config.RegistryCenter.CONNECT_URL);
        centerConfig.setProperty(ZookeeperConfig.AUTH_SCHEMA, Config.RegistryCenter.AUTH_SCHEMA);
        centerConfig.setProperty(ZookeeperConfig.AUTH_INFO, Config.RegistryCenter.AUTH_INFO);
        return centerConfig;
    }
}
