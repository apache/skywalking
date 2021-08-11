package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import io.grpc.ManagedChannelBuilder;
import org.apache.skywalking.banyandb.client.impl.BanyanDBGrpcClient;
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
import org.apache.skywalking.oap.server.core.storage.query.*;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.*;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

public class BanyanDBStorageProvider extends ModuleProvider {
    private BanyanDBStorageConfig config;
    private BanyanDBClient client;

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

        this.client = new BanyanDBClient(
                new BanyanDBGrpcClient(ManagedChannelBuilder.forAddress(config.getHost(), config.getPort()).build())
        );

        this.registerServiceImplementation(IBatchDAO.class, new BanyanDBBatchDAO(client));
        this.registerServiceImplementation(StorageDAO.class, new BanyanDBStorageDAO());

        this.registerServiceImplementation(INetworkAddressAliasDAO.class, new BanyanDBNetworkAddressAliasDAO());

        this.registerServiceImplementation(ITopologyQueryDAO.class, new BanyanDBTopologyQueryDAO());
        this.registerServiceImplementation(IMetricsQueryDAO.class, new BanyanDBMetricsQueryDAO());
        this.registerServiceImplementation(ITraceQueryDAO.class, new BanyanDBTraceQueryDAO(client));
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
