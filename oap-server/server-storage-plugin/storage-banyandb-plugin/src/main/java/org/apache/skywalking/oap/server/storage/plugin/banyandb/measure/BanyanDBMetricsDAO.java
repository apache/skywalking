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
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model);
        if (schema == null) {
            throw new IOException(model.getName() + " is not registered");
        }
        final Map<String, List<String>> seriesIDColumns = new HashMap<>();
        if (model.getBanyanDBModelExtension().isStoreIDTag()) {
            seriesIDColumns.put(BanyanDBConverter.ID, new ArrayList<>());
        } else {
            model.getColumns().forEach(c -> {
                BanyanDBExtension ext = c.getBanyanDBExtension();
                if (ext == null) {
                    return;
                }
                if (ext.isSeriesID()) {
                    seriesIDColumns.put(c.getColumnName().getName(), new ArrayList<>());
                }
            });
            if (seriesIDColumns.isEmpty()) {
                seriesIDColumns.put(ENTITY_ID, new ArrayList<>());
            }
        }

        String tc = model.getBanyanDBModelExtension().getTimestampColumn();
        final String tsCol = Strings.isBlank(tc) ? TIME_BUCKET : tc;
        long begin = 0L, end = 0L;
        StringBuilder idStr = new StringBuilder();
        for (Metrics m : metrics) {
            List<StorageID.Fragment> fragments = m.id().read();
            if (model.getBanyanDBModelExtension().isStoreIDTag()) {
                if (fragments.size() != 1) {
                    log.error("[{}]fragments' size is more than expected", fragments);
                    continue;
                }
                Object val = fragments.get(0).getValue();
                fragments =  Arrays.asList(new StorageID.Fragment(
                        new String[]{BanyanDBConverter.ID},
                        String.class,
                        true,
                        val));
            }
            AnalyticalResult result = analyze(fragments, tsCol, seriesIDColumns);

            idStr.append(result.cols()).append("=").append(m.id().build()).append(",");
            if (!result.success) {
                continue;
            }
            if (begin == 0 || result.begin < begin) {
                begin = result.begin;
            }
            if (end == 0 || result.end > end) {
                end = result.end;
            }
        }
        TimestampRange timestampRange = null;
        if (begin != 0L || end != 0L) {
            timestampRange = new TimestampRange(begin, end);
        } else {
            if (!model.getBanyanDBModelExtension().isIndexMode()) {
                log.info("{}[{}] will scan all blocks", model.getName(), idStr);
            }
        }

        List<Metrics> metricsInStorage = new ArrayList<>(metrics.size());
        MeasureQueryResponse resp = query(false, schema, schema.getTags(), schema.getFields(), timestampRange, new QueryBuilder<MeasureQuery>() {
                @Override
            protected void apply(MeasureQuery query) {
                seriesIDColumns.entrySet().forEach(entry -> {
                    if (!entry.getValue().isEmpty()) {
                        query.or(in(entry.getKey(), entry.getValue()));
                    }
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
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model);
        if (schema == null) {
            throw new IOException(model.getName() + " is not registered");
        }
        MeasureWrite measureWrite = getClient().createMeasureWrite(schema.getMetadata().getGroup(), // group name
                schema.getMetadata().name(), // measure-name
                TimeBucket.getTimestamp(metrics.getTimeBucket(), model.getDownsampling())); // timestamp
        final BanyanDBConverter.MeasureToStorage toStorage = new BanyanDBConverter.MeasureToStorage(schema, measureWrite);
        storageBuilder.entity2Storage(metrics, toStorage);
        if (model.getBanyanDBModelExtension().isStoreIDTag()) {
            toStorage.acceptID(metrics.id().build());
        }
        return new BanyanDBMeasureInsertRequest(toStorage.obtain(), callback);
    }

    @Override
    public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics, SessionCacheCallback callback) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model);
        if (schema == null) {
            throw new IOException(model.getName() + " is not registered");
        }
        MeasureWrite measureWrite = getClient().createMeasureWrite(schema.getMetadata().getGroup(), // group name
                schema.getMetadata().name(), // measure-name
                TimeBucket.getTimestamp(metrics.getTimeBucket(), model.getDownsampling())); // timestamp
        final BanyanDBConverter.MeasureToStorage toStorage = new BanyanDBConverter.MeasureToStorage(schema, measureWrite);
        storageBuilder.entity2Storage(metrics, toStorage);
        if (model.getBanyanDBModelExtension().isStoreIDTag()) {
            toStorage.acceptID(metrics.id().build());
        }
        return new BanyanDBMeasureUpdateRequest(toStorage.obtain());
    }

    private static class AnalyticalResult {
        private boolean success;
        private List<String[]> cols = new ArrayList<>();
        private long begin;
        private long end;

        private String cols() {
            StringBuilder b = new StringBuilder();
            for (String[] col : this.cols) {
                for (String c : col) {
                    b.append(c).append(",");
                }
                b.append(" ");
            }
            return b.toString();
        }
    }

    private AnalyticalResult analyze(List<StorageID.Fragment> fragments, String tsCol, Map<String, List<String>> seriesIDColumns) {
        AnalyticalResult result = new AnalyticalResult();
        for (StorageID.Fragment f : fragments) {
            Optional<String[]> cols = f.getName();
            if (cols.isPresent()) {
                result.cols.add(cols.get());
                for (String col : cols.get()) {
                    if (tsCol.equals(col)) {
                        long timeBucket = (long) f.getValue();
                        long epoch = TimeBucket.getTimestamp(timeBucket);
                        if (result.begin == 0 || epoch < result.begin) {
                            result.begin = epoch;
                        }
                        if (result.end == 0 || epoch > result.end) {
                            result.end = epoch;
                        }
                    } else if (seriesIDColumns.containsKey(col)) {
                        Preconditions.checkState(f.getType().equals(String.class));
                        seriesIDColumns.get(col).add((String) f.getValue());
                    } else {
                        log.error("col [{}] in fragment [{}] id [{}] is not ts or seriesID", col, f, fragments);
                        return result;
                    }
                }
            } else {
                log.error("fragment [{}] in id [{}] doesn't contains cols", f, fragments);
                return result;
            }
        }
        result.success = true;
        return result;
    }

}
