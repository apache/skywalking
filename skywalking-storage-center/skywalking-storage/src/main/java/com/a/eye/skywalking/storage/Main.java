package com.a.eye.skywalking.storage;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.logging.impl.log4j2.Log4j2Resolver;
import com.a.eye.skywalking.network.TransferService;
import com.a.eye.skywalking.network.TransferService.TransferServiceBuilder;
import com.a.eye.skywalking.registry.RegistryCenterFactory;
import com.a.eye.skywalking.registry.api.CenterType;
import com.a.eye.skywalking.registry.api.RegistryCenter;
import com.a.eye.skywalking.registry.impl.zookeeper.ZookeeperConfig;
import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.config.ConfigInitializer;
import com.a.eye.skywalking.storage.data.IndexDataCapacityMonitor;
import com.a.eye.skywalking.storage.notifier.SearchNotifier;
import com.a.eye.skywalking.storage.notifier.StorageNotifier;

import java.io.IOException;
import java.util.Properties;

/**
 * Created by xin on 2016/11/12.
 */
public class Main {

    private static ILog logger = LogManager.getLogger(Main.class);

    static {
        LogManager.setLogResolver(new Log4j2Resolver());
    }

    private static TransferService transferService;

    public static void main(String[] args) {
        try {
            initializeParam();

            transferService =
                    TransferServiceBuilder.newBuilder(Config.Server.PORT).startSpanStorageService(new StorageNotifier())
                            .startTraceSearchService(new SearchNotifier()).build();
            transferService.start();
            logger.info("transfer service started successfully!");
            new Thread(new IndexDataCapacityMonitor()).start();
            registryNode();
            logger.info("storage service started successfully!");
            Thread.currentThread().join();
        } catch (Throwable e) {
            logger.error("Failed to start service.", e);
        } finally {
            transferService.stop();
        }
    }

    private static void registryNode() {
        //TODO auth info  auth schema
        RegistryCenter registryCenter =
                RegistryCenterFactory.INSTANCE.getRegistryCenter(CenterType.DEFAULT_CENTER_TYPE);
        Properties registerConfig = new Properties();
        registerConfig.setProperty(ZookeeperConfig.CONNECT_URL, Config.RegistryCenter.CONNECT_URL);
        registryCenter.start(registerConfig);
    }

    private static void initializeParam() throws IllegalAccessException, IOException {
        Properties properties = new Properties();
        try {
            properties.load(Main.class.getResourceAsStream("/config.properties"));
            ConfigInitializer.initialize(properties, Config.class);
        } catch (IllegalAccessException e) {
            logger.error("Initialize the collect server configuration failed", e);
            throw e;
        } catch (IOException e) {
            logger.error("Initialize the collect server configuration failed", e);
            throw e;
        }
    }
}
