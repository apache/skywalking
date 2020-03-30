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
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

public class MetricsDAO implements IMetricsDAO {
    public static final String TAG_ENTITY_ID = "_entity_id";
    public static final String TAG_ENDPOINT_OWNER_SERVICE = "_service_id";
    public static final String TAG_ENDPOINT_NAME = "_endpoint_name";

    private final StorageBuilder<Metrics> storageBuilder;
    private final InfluxClient client;

    public MetricsDAO(InfluxClient client, StorageBuilder<Metrics> storageBuilder) {
        this.client = client;
        this.storageBuilder = storageBuilder;
    }

    @Override
    public List<Metrics> multiGet(Model model, List<String> ids) throws IOException {
        WhereQueryImpl<SelectQueryImpl> query = select()
            .regex("*::field")
            .from(client.getDatabase(), model.getName())
            .where(contains("id", Joiner.on("|").join(ids)));
        QueryResult.Series series = client.queryForSingleSeries(query);

        if (series == null) {
            return Collections.emptyList();
        }

        final List<Metrics> metrics = Lists.newArrayList();
        List<String> columns = series.getColumns();
        Map<String, String> storageAndColumnNames = Maps.newHashMap();
        for (ModelColumn column : model.getColumns()) {
            storageAndColumnNames.put(column.getColumnName().getStorageName(), column.getColumnName().getName());
        }

        series.getValues().forEach(values -> {
            Map<String, Object> data = Maps.newHashMap();

            for (int i = 1; i < columns.size(); i++) {
                Object value = values.get(i);
                if (value instanceof StorageDataComplexObject) {
                    value = ((StorageDataComplexObject) value).toStorageData();
                }

                data.put(storageAndColumnNames.get(columns.get(i)), value);
            }
            metrics.add(storageBuilder.map2Data(data));

        });

        return metrics;
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Metrics metrics) throws IOException {
        final long timestamp = TimeBucket.getTimestamp(metrics.getTimeBucket(), model.getDownsampling());
        if (metrics instanceof EndpointTraffic) {
            /**
             * @since 7.1.0 EndpointTraffic is a special manual metrics, to replace the old Endpoint Inventory.
             */
            return new InfluxInsertRequest(model, metrics, storageBuilder)
                .time(timestamp, TimeUnit.MILLISECONDS)
                .addFieldAsTag(EndpointTraffic.SERVICE_ID, TAG_ENDPOINT_OWNER_SERVICE)
                .addFieldAsTag(EndpointTraffic.NAME, TAG_ENDPOINT_NAME);
        } else {
            return new InfluxInsertRequest(model, metrics, storageBuilder)
                .time(timestamp, TimeUnit.MILLISECONDS)
                .addFieldAsTag(Metrics.ENTITY_ID, TAG_ENTITY_ID);
        }
    }

    @Override
    public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics) throws IOException {
        return (UpdateRequest) this.prepareBatchInsert(model, metrics);
    }
}
