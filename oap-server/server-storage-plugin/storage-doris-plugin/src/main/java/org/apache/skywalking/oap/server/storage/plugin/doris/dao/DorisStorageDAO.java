package org.apache.skywalking.oap.server.storage.plugin.doris.dao;

import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.core.storage.INoneStreamDAO;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasCacheDAO;
import org.apache.skywalking.oap.server.core.storage.management.UIMenuManagementDAO;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.storage.plugin.doris.StorageModuleDorisConfig;
import org.apache.skywalking.oap.server.storage.plugin.doris.client.DorisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DorisStorageDAO implements StorageDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(DorisStorageDAO.class);
    private final DorisClient dorisClient;
    private final StorageModuleDorisConfig config; // Added config

    public DorisStorageDAO(DorisClient dorisClient, StorageModuleDorisConfig config) { // Updated constructor
        this.dorisClient = dorisClient;
        this.config = config; // Store config
    }

    @Override
    public IMetricsDAO newMetricsDao(StorageBuilder<?> storageBuilder) {
        // The storageBuilder is not directly used by DorisMetricsDAO constructor in the current setup,
        // but it's part of the factory method signature.
        // DorisMetricsDAO internally handles metric-to-map conversion for now.
        LOGGER.debug("Creating new DorisMetricsDAO instance.");
        return new DorisMetricsDAO(this.dorisClient);
    }

    @Override
    public IRecordDAO newRecordDao(StorageBuilder<?> storageBuilder) {
        // The storageBuilder is not directly used by DorisRecordDAO constructor in the current setup,
        // but it's part of the factory method signature.
        LOGGER.debug("Creating new DorisRecordDAO instance.");
        return new DorisRecordDAO(this.dorisClient);
    }

    @Override
    public INoneStreamDAO newNoneStreamDao(StorageBuilder<?> storageBuilder) {
        LOGGER.warn("newNoneStreamDao not implemented yet, returning null.");
         // return new DorisNoneStreamDAO(this.dorisClient, storageBuilder); // Placeholder for actual implementation
        return null;
    }
    
    // The StorageDAO interface from the codebase seems to be more extensive than initially read.
    // Adding other newXyzDAO methods as per typical SkyWalking StorageDAO implementations.
    // These will also be stubs for now.

    @Override
    public IManagementDAO newManagementDao(StorageBuilder<?> storageBuilder) {
        LOGGER.warn("newManagementDao not implemented yet, returning null.");
        // return new DorisManagementDAO(dorisClient, storageBuilder); // Placeholder
        return null;
    }

    // Based on common StorageDAO interfaces, adding more stubs.
    // Users should verify against the exact StorageDAO interface in their SkyWalking version.

    public INetworkAddressAliasDAO newNetworkAddressAliasDAO() {
        LOGGER.warn("newNetworkAddressAliasDAO not implemented yet, returning null.");
        // return new DorisNetworkAddressAliasDAO(this.dorisClient); // Placeholder
        return null;
    }

    public IBatchDAO newBatchDAO() {
        LOGGER.debug("Creating new DorisBatchDAO instance.");
        return new DorisBatchDAO(this.dorisClient, this.config);
    }

    public IHistoryDeleteDAO newHistoryDeleteDAO() {
        LOGGER.debug("Creating new DorisHistoryDeleteDAO instance.");
        return new DorisHistoryDeleteDAO(this.dorisClient);
    }
    
    // It's likely the StorageDAO interface is an aggregation of many more specific DAO factories.
    // The methods below are common in various storage plugins' StorageDAO implementations.
    // Without the exact, complete StorageDAO interface for this SkyWalking version,
    // these are added based on common patterns.

    public ILogQueryDAO newLogQueryDAO() {
        LOGGER.warn("newLogQueryDAO not implemented yet, returning null.");
        return null;
    }

    public IMetricsQueryDAO newMetricsQueryDAO() {
         LOGGER.warn("newMetricsQueryDAO not implemented yet, returning null.");
        return null;
    }
    
    public ITraceQueryDAO newTraceQueryDAO() {
        LOGGER.warn("newTraceQueryDAO not implemented yet, returning null.");
        return null;
    }

    public IMetadataQueryDAO newMetadataQueryDAO() {
        LOGGER.warn("newMetadataQueryDAO not implemented yet, returning null.");
        return null;
    }

    public IAggregationQueryDAO newAggregationQueryDAO() {
        LOGGER.warn("newAggregationQueryDAO not implemented yet, returning null.");
        return null;
    }

    public IAlarmQueryDAO newAlarmQueryDAO() {
        LOGGER.warn("newAlarmQueryDAO not implemented yet, returning null.");
        return null;
    }

    public ITopologyQueryDAO newTopologyQueryDAO() {
        LOGGER.warn("newTopologyQueryDAO not implemented yet, returning null.");
        return null;
    }

    public IProfileTaskQueryDAO newProfileTaskQueryDAO() {
        LOGGER.warn("newProfileTaskQueryDAO not implemented yet, returning null.");
        return null;
    }

    public IProfileTaskLogQueryDAO newProfileTaskLogQueryDAO() {
        LOGGER.warn("newProfileTaskLogQueryDAO not implemented yet, returning null.");
        return null;
    }
    
    public IProfileThreadSnapshotQueryDAO newProfileThreadSnapshotQueryDAO() {
        LOGGER.warn("newProfileThreadSnapshotQueryDAO not implemented yet, returning null.");
        return null;
    }

    public UITemplateManagementDAO newUITemplateManagementDAO() {
        LOGGER.warn("newUITemplateManagementDAO not implemented yet, returning null.");
        return null;
    }
    
    public UIMenuManagementDAO newUIMenuManagementDAO() {
        LOGGER.warn("newUIMenuManagementDAO not implemented yet, returning null.");
        return null;
    }

    public IEBPFProfilingTaskDAO newEBPFProfilingTaskDAO() {
        LOGGER.warn("newEBPFProfilingTaskDAO not implemented yet, returning null.");
        return null;
    }

    public IEBPFProfilingScheduleDAO newEBPFProfilingScheduleDAO() {
        LOGGER.warn("newEBPFProfilingScheduleDAO not implemented yet, returning null.");
        return null;
    }

    public IEBPFProfilingDataDAO newEBPFProfilingDataDAO() {
        LOGGER.warn("newEBPFProfilingDataDAO not implemented yet, returning null.");
        return null;
    }

    public IEventQueryDAO newEventQueryDAO() {
        LOGGER.warn("newEventQueryDAO not implemented yet, returning null.");
        return null;
    }
    
    public IBrowserLogQueryDAO newBrowserLogQueryDAO() {
         LOGGER.warn("newBrowserLogQueryDAO not implemented yet, returning null.");
        return null;
    }

    public ISpanAttachedEventQueryDAO newSpanAttachedEventQueryDAO() {
        LOGGER.warn("newSpanAttachedEventQueryDAO not implemented yet, returning null.");
        return null;
    }

    public IZipkinQueryDAO newZipkinQueryDAO() {
        LOGGER.warn("newZipkinQueryDAO not implemented yet, returning null.");
        return null;
    }

    public ITagAutoCompleteQueryDAO newTagAutoCompleteQueryDAO() {
        LOGGER.warn("newTagAutoCompleteQueryDAO not implemented yet, returning null.");
        return null;
    }
}
