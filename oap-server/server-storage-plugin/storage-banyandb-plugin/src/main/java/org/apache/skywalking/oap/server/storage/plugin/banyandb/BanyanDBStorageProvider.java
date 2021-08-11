package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBAlarmQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBBatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBHistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBMetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBNetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBStorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBTopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBTopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBTraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBUITemplateManagementDAO;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

public class BanyanDBStorageProvider extends ModuleProvider {
    private BanyanDBStorageConfig config;

    public BanyanDBStorageProvider() {
        this.config = new BanyanDBStorageConfig();
    }

    @Override
    public String name() {
        return "banyandb";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return StorageModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        this.registerServiceImplementation(StorageBuilderFactory.class, new StorageBuilderFactory.Default());

        // TODO: create banyandb client

        this.registerServiceImplementation(IBatchDAO.class, new BanyanDBBatchDAO());
        this.registerServiceImplementation(StorageDAO.class, new BanyanDBStorageDAO());

        this.registerServiceImplementation(INetworkAddressAliasDAO.class, new BanyanDBNetworkAddressAliasDAO());

        this.registerServiceImplementation(ITopologyQueryDAO.class, new BanyanDBTopologyQueryDAO());
        this.registerServiceImplementation(IMetricsQueryDAO.class, new BanyanDBMetricsQueryDAO());
        this.registerServiceImplementation(ITraceQueryDAO.class, new BanyanDBTraceQueryDAO());
        this.registerServiceImplementation(IBrowserLogQueryDAO.class, new BanyanDBBrowserLogQueryDAO());
        this.registerServiceImplementation(IMetadataQueryDAO.class, new BanyanDBMetadataQueryDAO());
        this.registerServiceImplementation(IAggregationQueryDAO.class, new BanyanDBAggregationQueryDAO());
        this.registerServiceImplementation(IAlarmQueryDAO.class, new BanyanDBAlarmQueryDAO());
        this.registerServiceImplementation(IHistoryDeleteDAO.class, new BanyanDBHistoryDeleteDAO());
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new BanyanDBTopNRecordsQueryDAO());
        this.registerServiceImplementation(ILogQueryDAO.class, new BanyanDBLogQueryDAO());

        this.registerServiceImplementation(IProfileTaskQueryDAO.class, new BanyanDBProfileTaskQueryDAO());
        this.registerServiceImplementation(IProfileTaskLogQueryDAO.class, new BanyanDBProfileTaskLogQueryDAO());
        this.registerServiceImplementation(
                IProfileThreadSnapshotQueryDAO.class, new BanyanDBProfileThreadSnapshotQueryDAO());
        this.registerServiceImplementation(UITemplateManagementDAO.class, new BanyanDBUITemplateManagementDAO());

        this.registerServiceImplementation(IEventQueryDAO.class, new BanyanDBEventQueryDAO());
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        final ConfigService configService = getManager().find(CoreModule.NAME)
                .provider()
                .getService(ConfigService.class);

        MetricsCreator metricCreator = getManager().find(TelemetryModule.NAME)
                .provider()
                .getService(MetricsCreator.class);
        HealthCheckMetrics healthChecker = metricCreator.createHealthCheckerGauge(
                "storage_banyandb", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        // TODO: health checker,
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public String[] requiredModules() {
        return new String[]{CoreModule.NAME};
    }
}
