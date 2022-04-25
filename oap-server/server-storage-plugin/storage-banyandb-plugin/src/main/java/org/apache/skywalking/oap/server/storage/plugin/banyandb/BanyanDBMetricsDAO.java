package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.client.MeasureWrite;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class BanyanDBMetricsDAO implements IMetricsDAO {
    private final StorageBuilder<Metrics> storageBuilder;

    @Override
    public List<Metrics> multiGet(Model model, List<Metrics> metrics) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Metrics metrics) throws IOException {
        log.info("prepare to insert {}", model.getName());
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model.getName());
        if (schema == null) {
            throw new IOException(model.getName() + " is not registered");
        }
        MeasureWrite measureWrite = new MeasureWrite(schema.getMetadata().getGroup(), // group name
                model.getName(), // index-name
                TimeBucket.getTimestamp(metrics.getTimeBucket(), model.getDownsampling())); // timestamp
        final Convert2Storage<MeasureWrite> toStorage = new BanyanDBConverter.MeasureToStorage(schema, measureWrite);
        storageBuilder.entity2Storage(metrics, toStorage);
        return new InsertRequest() {
        };
    }

    @Override
    public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics) throws IOException {
        return new UpdateRequest() {
        };
    }
}
