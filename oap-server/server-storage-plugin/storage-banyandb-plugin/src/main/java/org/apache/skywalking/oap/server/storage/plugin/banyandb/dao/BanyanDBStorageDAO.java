package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.INoneStreamDAO;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class BanyanDBStorageDAO implements StorageDAO {
    @Override
    public IMetricsDAO newMetricsDao(StorageBuilder storageBuilder) {
        return new IMetricsDAO() {
            @Override
            public List<Metrics> multiGet(Model model, List<Metrics> metrics) throws IOException {
                return Collections.emptyList();
            }

            @Override
            public InsertRequest prepareBatchInsert(Model model, Metrics metrics) throws IOException {
                return new InsertRequest() {
                };
            }

            @Override
            public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics) throws IOException {
                return new UpdateRequest() {
                };
            }
        };
    }

    @Override
    public IRecordDAO newRecordDao(StorageBuilder storageBuilder) {
        try {
            if (SegmentRecord.class.equals(storageBuilder.getClass().getMethod("storage2Entity", Map.class).getReturnType())) {
                return new BanyanDBRecordDAO(new BanyanDBSegmentRecordBuilder());
            } else {
                return (model, record) -> new InsertRequest() {
                };
            }
        } catch (NoSuchMethodException noSuchMethodException) {
            log.error("cannot find method storage2Entity", noSuchMethodException);
            throw new RuntimeException("cannot find method storage2Entity");
        }
    }

    @Override
    public INoneStreamDAO newNoneStreamDao(StorageBuilder storageBuilder) {
        return new INoneStreamDAO() {
            @Override
            public void insert(Model model, NoneStream noneStream) throws IOException {
            }
        };
    }

    @Override
    public IManagementDAO newManagementDao(StorageBuilder storageBuilder) {
        return new IManagementDAO() {
            @Override
            public void insert(Model model, ManagementData storageData) throws IOException {
            }
        };
    }
}
