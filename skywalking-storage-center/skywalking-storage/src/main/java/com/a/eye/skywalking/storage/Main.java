package com.a.eye.skywalking.storage;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.logging.impl.log4j2.Log4j2Resolver;
import com.a.eye.skywalking.network.ServiceProvider;
import com.a.eye.skywalking.registry.RegistryCenterFactory;
import com.a.eye.skywalking.registry.api.CenterType;
import com.a.eye.skywalking.registry.api.RegistryCenter;
import com.a.eye.skywalking.registry.impl.zookeeper.ZookeeperConfig;
import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.config.ConfigInitializer;
import com.a.eye.skywalking.storage.data.file.DataFilesManager;
import com.a.eye.skywalking.storage.data.index.IndexOperatorFactory;
import com.a.eye.skywalking.storage.listener.SearchListener;
import com.a.eye.skywalking.storage.listener.StorageListener;
import com.a.eye.skywalking.storage.util.NetUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static com.a.eye.skywalking.storage.config.Config.RegistryCenter.PATH_PREFIX;

/**
 * Created by xin on 2016/11/12.
 */
public class Main {

    private static final ILog logger = LogManager.getLogger(Main.class);
    private static final String SERVER_REPORTER_NAME = "DataConsumer Server";

    static {
        LogManager.setLogResolver(new Log4j2Resolver());
    }

    private static ServiceProvider provider;

    public static void main(String[] args) {
        try {
            initializeParam();
            HealthCollector.init(SERVER_REPORTER_NAME);

            IndexOperatorFactory.initOperatorPool();

            DataFilesManager.init();

            provider = ServiceProvider.newBuilder(Config.Server.PORT).addSpanStorageService(new StorageListener())
                    .addAsyncTraceSearchService(new SearchListener()).build();
            provider.start();

            if (logger.isDebugEnable()) {
                logger.debug("Service provider started.");
            }

            registryNode();

            logger.info("SkyWalking storage server started.");
            Thread.currentThread().join();
        } catch (Throwable e) {
            logger.error("SkyWalking storage server start failure.", e);
        } finally {
            provider.stop();
        }
    }

    private static void registryNode() {
        RegistryCenter registryCenter =
                RegistryCenterFactory.INSTANCE.getRegistryCenter(CenterType.DEFAULT_CENTER_TYPE);
        Properties registerConfig = new Properties();
        registerConfig.setProperty(ZookeeperConfig.CONNECT_URL, Config.RegistryCenter.CONNECT_URL);
        registerConfig.setProperty(ZookeeperConfig.AUTH_SCHEMA, Config.RegistryCenter.AUTH_SCHEMA);
        registerConfig.setProperty(ZookeeperConfig.AUTH_INFO, Config.RegistryCenter.AUTH_INFO);
        registryCenter.start(registerConfig);
        registryCenter.register(
                PATH_PREFIX + NetUtils.getLocalAddress().getHostAddress() + ":" + Config.Server.PORT);
    }

    private static void initializeParam() throws IllegalAccessException, IOException {
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
