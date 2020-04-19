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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.base;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.TableMetaInfo;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants.ALL_FIELDS;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class MetricsDAO implements IMetricsDAO {

    private final StorageBuilder<Metrics> storageBuilder;
    private final InfluxClient client;

    public MetricsDAO(InfluxClient client, StorageBuilder<Metrics> storageBuilder) {
        this.client = client;
        this.storageBuilder = storageBuilder;
    }

    @Override
    public List<Metrics> multiGet(Model model, List<String> ids) throws IOException {
        WhereQueryImpl<SelectQueryImpl> query = select()
            .raw(ALL_FIELDS)
            .from(client.getDatabase(), model.getName())
            .where(contains("id", Joiner.on("|").join(ids)));
        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result: {}", query.getCommand(), series);
        }

        if (series == null) {
            return Collections.emptyList();
        }

        final List<Metrics> metrics = Lists.newArrayList();
        List<String> columns = series.getColumns();

        TableMetaInfo metaInfo = TableMetaInfo.get(model.getName());
        Map<String, String> storageAndColumnMap = metaInfo.getStorageAndColumnMap();

        series.getValues().forEach(values -> {
            Map<String, Object> data = Maps.newHashMap();

            for (int i = 1; i < columns.size(); i++) {
                Object value = values.get(i);
                if (value instanceof StorageDataComplexObject) {
                    value = ((StorageDataComplexObject) value).toStorageData();
                }

                data.put(storageAndColumnMap.get(columns.get(i)), value);
            }
            metrics.add(storageBuilder.map2Data(data));

        });

        return metrics;
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Metrics metrics) throws IOException {
        final long timestamp = TimeBucket.getTimestamp(metrics.getTimeBucket(), model.getDownsampling());
        TableMetaInfo tableMetaInfo = TableMetaInfo.get(model.getName());

        final InfluxInsertRequest request = new InfluxInsertRequest(model, metrics, storageBuilder)
            .time(timestamp, TimeUnit.MILLISECONDS);

        tableMetaInfo.getStorageAndTagMap().forEach((field, tag) -> {
            request.addFieldAsTag(field, tag);
        });
        return request;
    }

    @Override
    public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics) throws IOException {
        return (UpdateRequest) this.prepareBatchInsert(model, metrics);
    }
}
