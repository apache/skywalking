package org.apache.skywalking.oap.server.storage.plugin.doris;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.doris.client.DorisClient;
import org.apache.skywalking.oap.server.storage.plugin.doris.dao.DorisStorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.doris.query.DorisMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.doris.query.DorisTraceQueryDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StorageModuleDorisProvider extends ModuleProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageModuleDorisProvider.class);

    private StorageModuleDorisConfig config;
    private DorisClient dorisClient;

    @Override
    public String name() {
        return "doris";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return StorageModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<StorageModuleDorisConfig>() {
            @Override
            public Class type() {
                return StorageModuleDorisConfig.class;
            }

            @Override
            public void onInitialized(final StorageModuleDorisConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
        this.dorisClient = new DorisClient(config);
        LOGGER.info("Doris storage provider prepare method, config host: {}", config.getHost());

        this.registerServiceImplementation(StorageDAO.class, new DorisStorageDAO(dorisClient));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new DorisMetricsQueryDAO(dorisClient));
        this.registerServiceImplementation(ITraceQueryDAO.class, new DorisTraceQueryDAO(dorisClient, config));
        
        LOGGER.info("Doris DAOs registered in provider.");
    }

    @Override
    public void start() throws ModuleStartException {
        try {
            dorisClient.connect();
            LOGGER.info("Successfully connected to Doris in provider start method.");
        } catch (Exception e) {
            LOGGER.error("Failed to connect to Doris in provider start method.", e);
            throw new ModuleStartException("Failed to connect to Doris.", e);
        }
    }

    @Override
    public void notifyAfterCompleted() {
        // Nothing to do
    }

    @Override
    public String[] requiredModules() {
        return new String[]{CoreModule.NAME};
    }
}
