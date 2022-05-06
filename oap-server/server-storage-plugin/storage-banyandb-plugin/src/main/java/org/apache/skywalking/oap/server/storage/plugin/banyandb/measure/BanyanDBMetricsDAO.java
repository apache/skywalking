package org.apache.skywalking.oap.server.storage.plugin.banyandb.measure;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.MeasureWrite;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class BanyanDBMetricsDAO extends AbstractBanyanDBDAO implements IMetricsDAO {
    private final StorageBuilder<Metrics> storageBuilder;

    public BanyanDBMetricsDAO(BanyanDBStorageClient client, StorageBuilder<Metrics> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    @Override
    public List<Metrics> multiGet(Model model, List<Metrics> metrics) throws IOException {
        log.info("multiGet {} from BanyanDB", model.getName());
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model.getName());
        if (schema == null) {
            throw new IOException(model.getName() + " is not registered");
        }
        List<Metrics> metricsInStorage = new ArrayList<>(metrics.size());
        for (final Metrics missCachedMetric : metrics) {
            MeasureQueryResponse resp = query(model.getName(), schema.getTags(), schema.getFields(), new QueryBuilder<MeasureQuery>() {
                @Override
                protected void apply(MeasureQuery query) {
                    query.andWithID(missCachedMetric.id());
                    if (model.getName().endsWith("_traffic")) {
                        switch (model.getName()) {
                            case ProcessTraffic.INDEX_NAME:
                                query.and(eq(ProcessTraffic.SERVICE_ID, ((ProcessTraffic) missCachedMetric).getServiceId()));
                                break;
                            case InstanceTraffic.INDEX_NAME:
                                query.and(eq(InstanceTraffic.SERVICE_ID, ((InstanceTraffic) missCachedMetric).getServiceId()));
                                break;
                            case EndpointTraffic.INDEX_NAME:
                                query.and(eq(EndpointTraffic.SERVICE_ID, ((EndpointTraffic) missCachedMetric).getServiceId()));
                                break;
                            case ServiceTraffic.INDEX_NAME:
                                query.and(eq(ServiceTraffic.NAME, ((ServiceTraffic) missCachedMetric).getName()));
                                break;
                            default:
                                throw new IllegalStateException("Unknown metadata type, " + model.getName());
                        }
                    } else {
                        query.and(eq(Metrics.TIME_BUCKET, missCachedMetric.getTimeBucket()));
                    }
                }
            });
            if (resp.size() == 0) {
                continue;
            }
            for (final DataPoint dataPoint : resp.getDataPoints()) {
                metricsInStorage.add(storageBuilder.storage2Entity(new BanyanDBConverter.StorageToMeasure(model.getName(), dataPoint)));
            }
        }
        return metricsInStorage;
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
        final BanyanDBConverter.MeasureToStorage toStorage = new BanyanDBConverter.MeasureToStorage(schema, measureWrite);
        storageBuilder.entity2Storage(metrics, toStorage);
        toStorage.acceptID(metrics.id());
        return new BanyanDBMeasureInsertRequest(toStorage.obtain());
    }

    @Override
    public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics) throws IOException {
        return new UpdateRequest() {
        };
    }
}
