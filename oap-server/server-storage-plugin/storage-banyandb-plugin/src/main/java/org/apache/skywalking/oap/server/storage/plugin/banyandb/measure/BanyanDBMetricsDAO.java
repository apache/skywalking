/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.storage.plugin.banyandb.measure;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.MeasureWrite;
import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBExtension;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Metric;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.skywalking.oap.server.core.analysis.metrics.Metrics.ENTITY_ID;
import static org.apache.skywalking.oap.server.core.storage.StorageData.TIME_BUCKET;

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
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model);
        if (schema == null) {
            throw new IOException(model.getName() + " is not registered");
        }
        String tc = model.getBanyanDBModelExtension().getTimestampColumn();
        final String tsCol = Strings.isBlank(tc) ?  TIME_BUCKET : tc;
        final AtomicLong begin = new AtomicLong(), end = new AtomicLong();
        final Map<String, List<String>> seriesIDColumns = new HashMap<>();
        model.getColumns().forEach(c -> {
            BanyanDBExtension ext = c.getBanyanDBExtension();
            if (ext == null) {
                return;
            }
            if (ext.isShardingKey()) {
                seriesIDColumns.put(c.getColumnName().getName(), new ArrayList<>());
            }
        });
        if (seriesIDColumns.isEmpty()) {
            seriesIDColumns.put(ENTITY_ID, new ArrayList<>());
        }
        for (Metrics m:metrics) {
            StorageID id = m.id();
            List<StorageID.Fragment> fragments = id.read();
            for (StorageID.Fragment f:fragments) {
                Optional<String[]> cols = f.getName();
                if (cols.isPresent()) {
                    String col = cols.get()[0];
                    if (tsCol.equals(col)) {
                       long timeBucket =  (long) f.getValue();
                       long epoch = TimeBucket.getTimestamp(timeBucket);
                       if (epoch < begin.longValue()) {
                           begin.set(epoch);
                       }
                       if (epoch > end.longValue()) {
                           end.set(epoch);
                       }
                    } else if (seriesIDColumns.containsKey(col)) {
                        Preconditions.checkState(f.getType().equals(String.class));
                        seriesIDColumns.get(col).add((String) f.getValue());
                    } else {
                        throw new InvalidObjectException(String.format("fragment [%s] in id [%s] is unknown", f, id.build()));
                    }
                } else {
                    throw new InvalidObjectException(String.format("fragment [%s] is mutant id [%s] is unknown", f, id.build()));
                }
            }
        }

        List<Metrics> metricsInStorage = new ArrayList<>(metrics.size());
        MeasureQueryResponse resp = query(model.getName(), schema.getTags(), schema.getFields(), new TimestampRange(begin.longValue(), end.longValue()), new QueryBuilder<MeasureQuery>() {
            @Override
            protected void apply(MeasureQuery query) {
                seriesIDColumns.entrySet().forEach(entry -> {
                    query.or(in(entry.getKey(), entry.getValue()));
                });
            }
        });

        if (resp.size() == 0) {
            return Collections.emptyList();
        }

        for (final DataPoint dataPoint : resp.getDataPoints()) {
            metricsInStorage.add(storageBuilder.storage2Entity(new BanyanDBConverter.StorageToMeasure(schema, dataPoint)));
        }
        return metricsInStorage;
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Metrics metrics, SessionCacheCallback callback) throws IOException {
        log.info("prepare to insert {}", model.getName());
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model);
        if (schema == null) {
            throw new IOException(model.getName() + " is not registered");
        }
        MeasureWrite measureWrite = new MeasureWrite(schema.getMetadata().getGroup(), // group name
                schema.getMetadata().name(), // measure-name
                TimeBucket.getTimestamp(metrics.getTimeBucket(), model.getDownsampling())); // timestamp
        final BanyanDBConverter.MeasureToStorage toStorage = new BanyanDBConverter.MeasureToStorage(schema, measureWrite);
        storageBuilder.entity2Storage(metrics, toStorage);
        toStorage.acceptID(metrics.id().build());
        return new BanyanDBMeasureInsertRequest(toStorage.obtain(), callback);
    }

    @Override
    public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics, SessionCacheCallback callback) throws IOException {
        log.info("prepare to update {}", model.getName());
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model);
        if (schema == null) {
            throw new IOException(model.getName() + " is not registered");
        }
        MeasureWrite measureWrite = new MeasureWrite(schema.getMetadata().getGroup(), // group name
                schema.getMetadata().name(), // measure-name
                TimeBucket.getTimestamp(metrics.getTimeBucket(), model.getDownsampling())); // timestamp
        final BanyanDBConverter.MeasureToStorage toStorage = new BanyanDBConverter.MeasureToStorage(schema, measureWrite);
        storageBuilder.entity2Storage(metrics, toStorage);
        toStorage.acceptID(metrics.id().build());
        return new BanyanDBMeasureUpdateRequest(toStorage.obtain());
    }
}
