package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.INoneStreamDAO;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

public class BanyanDBStorageDAO implements StorageDAO {
    @Override
    public IMetricsDAO newMetricsDao(StorageBuilder storageBuilder) {
        return null;
    }

    @Override
    public IRecordDAO newRecordDao(StorageBuilder storageBuilder) {
        return null;
    }

    @Override
    public INoneStreamDAO newNoneStreamDao(StorageBuilder storageBuilder) {
        return null;
    }

    @Override
    public IManagementDAO newManagementDao(StorageBuilder storageBuilder) {
        return null;
    }
}
