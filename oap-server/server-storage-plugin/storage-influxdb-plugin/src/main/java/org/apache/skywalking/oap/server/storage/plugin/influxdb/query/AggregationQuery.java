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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.query.entity.Order;
import org.apache.skywalking.oap.server.core.query.entity.TopNEntity;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.storage.model.ModelName;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.base.MetricsDAO;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.SelectSubQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class AggregationQuery implements IAggregationQueryDAO {
    private InfluxClient client;

    public AggregationQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public List<TopNEntity> getServiceTopN(String indName, String valueCName, int topN, Downsampling downsampling,
                                           long startTB, long endTB, Order order) throws IOException {
        return getTopNEntity(downsampling, indName, subQuery(indName, valueCName, startTB, endTB), order, topN);
    }

    @Override
    public List<TopNEntity> getAllServiceInstanceTopN(String indName, String valueCName, int topN,
                                                      Downsampling downsampling,
                                                      long startTB, long endTB, Order order) throws IOException {
        return getTopNEntity(downsampling, indName, subQuery(indName, valueCName, startTB, endTB), order, topN);
    }

    @Override
    public List<TopNEntity> getServiceInstanceTopN(int serviceId, String indName, String valueCName, int topN,
                                                   Downsampling downsampling,
                                                   long startTB, long endTB, Order order) throws IOException {
        return getTopNEntity(
            downsampling, indName,
            subQuery(ServiceInstanceInventory.SERVICE_ID, serviceId, indName, valueCName, startTB, endTB), order, topN
        );
    }

    @Override
    public List<TopNEntity> getAllEndpointTopN(String indName, String valueCName, int topN, Downsampling downsampling,
                                               long startTB, long endTB, Order order) throws IOException {
        return getTopNEntity(downsampling, indName, subQuery(indName, valueCName, startTB, endTB), order, topN);
    }

    @Override
    public List<TopNEntity> getEndpointTopN(int serviceId, String indName, String valueCName, int topN,
                                            Downsampling downsampling,
                                            long startTB, long endTB, Order order) throws IOException {
        return getTopNEntity(
            downsampling, indName,
            subQuery(EndpointTraffic.SERVICE_ID, serviceId, indName, valueCName, startTB, endTB), order, topN
        );
    }

    private List<TopNEntity> getTopNEntity(Downsampling downsampling,
                                           String name,
                                           SelectSubQueryImpl<SelectQueryImpl> subQuery,
                                           Order order,
                                           int topN) throws IOException {
        String measurement = ModelName.build(downsampling, name);
        // Have to re-sort here. Because the function, top()/bottom(), get the result ordered by the `time`.
        Comparator<TopNEntity> comparator = DESCENDING;
        String functionName = "top";
        if (order == Order.ASC) {
            functionName = "bottom";
            comparator = ASCENDING;
        }

        SelectQueryImpl query = select().function(functionName, "mean", topN).as("value")
                                        .column(MetricsDAO.TAG_ENTITY_ID)
                                        .from(client.getDatabase(), measurement);
        query.setSubQuery(subQuery);

        List<QueryResult.Series> series = client.queryForSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null || series.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<Object>> dataset = series.get(0).getValues();
        List<TopNEntity> entities = Lists.newArrayListWithCapacity(dataset.size());
        dataset.forEach(values -> {
            final TopNEntity entity = new TopNEntity();
            entity.setId((String) values.get(2));
            entity.setValue(((Double) values.get(1)).longValue());
            entities.add(entity);
        });

        Collections.sort(entities, comparator); // re-sort by self, because of the result order by time.
        return entities;
    }

    private SelectSubQueryImpl<SelectQueryImpl> subQuery(String serviceColumnName, int serviceId, String name,
                                                         String columnName,
                                                         long startTB, long endTB) {
        return select().fromSubQuery(client.getDatabase()).mean(columnName).from(name)
                       .where()
                       .and(eq(serviceColumnName, serviceId))
                       .and(gte(InfluxClient.TIME, InfluxClient.timeInterval(startTB)))
                       .and(lte(InfluxClient.TIME, InfluxClient.timeInterval(endTB)))
                       .groupBy(MetricsDAO.TAG_ENTITY_ID);
    }

    private SelectSubQueryImpl<SelectQueryImpl> subQuery(String name, String columnName, long startTB, long endTB) {
        return select().fromSubQuery(client.getDatabase()).mean(columnName).from(name)
                       .where()
                       .and(gte(InfluxClient.TIME, InfluxClient.timeInterval(startTB)))
                       .and(lte(InfluxClient.TIME, InfluxClient.timeInterval(endTB)))
                       .groupBy(MetricsDAO.TAG_ENTITY_ID);
    }

    private static final Comparator<TopNEntity> ASCENDING = Comparator.comparingLong(TopNEntity::getValue);

    private static final Comparator<TopNEntity> DESCENDING = (a, b) -> Long.compare(b.getValue(), a.getValue());
}
