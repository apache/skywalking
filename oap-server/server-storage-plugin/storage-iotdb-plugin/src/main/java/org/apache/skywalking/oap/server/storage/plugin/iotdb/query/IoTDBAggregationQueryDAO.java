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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBIndexes;

@Slf4j
@RequiredArgsConstructor
public class IoTDBAggregationQueryDAO implements IAggregationQueryDAO {
    private final IoTDBClient client;

    @Override
    public List<SelectedRecord> sortMetrics(TopNCondition condition, String valueColumnName, Duration duration,
                                            List<KeyValue> additionalConditions) throws IOException {
        // This method maybe have poor efficiency. It queries all data which meets a condition without aggregation function.
        // https://github.com/apache/iotdb/issues/4006
        StringBuilder query = new StringBuilder();
        query.append(String.format("select %s from ", valueColumnName));
        query = client.addModelPath(query, condition.getName());

        Map<String, String> indexAndValueMap = new HashMap<>();
        List<KeyValue> measurementConditions = new ArrayList<>();
        if (additionalConditions != null) {
            for (KeyValue additionalCondition : additionalConditions) {
                String key = additionalCondition.getKey();
                if (IoTDBIndexes.isIndex(key)) {
                    indexAndValueMap.put(key, additionalCondition.getValue());
                } else {
                    measurementConditions.add(additionalCondition);
                }
            }
        }
        if (!indexAndValueMap.isEmpty()) {
            query = client.addQueryIndexValue(condition.getName(), query, indexAndValueMap);
        } else {
            query = client.addQueryAsterisk(condition.getName(), query);
        }

        query.append(" where ").append(IoTDBClient.TIME).append(" >= ").append(duration.getStartTimestamp())
                .append(" and ").append(IoTDBClient.TIME).append(" <= ").append(duration.getEndTimestamp());
        if (!measurementConditions.isEmpty()) {
            for (KeyValue measurementCondition : measurementConditions) {
                query.append(" and ").append(measurementCondition.getKey()).append(" = \"")
                        .append(measurementCondition.getValue()).append("\"");
            }
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        List<SelectedRecord> topEntities = new ArrayList<>();
        try {
            wrapper = sessionPool.executeQueryStatement(query.toString());
            if (log.isDebugEnabled()) {
                log.debug("SQL: {}, columnNames: {}", query, wrapper.getColumnNames());
            }

            Map<String, Double> entityIdAndSumMap = new HashMap<>();
            Map<String, Integer> entityIdAndCountMap = new HashMap<>();
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                String[] layerNames = fields.get(0).getStringValue().split("\\" + IoTDBClient.DOT + "\"");
                String entityId = client.layerName2IndexValue(layerNames[2]);
                double value = Double.parseDouble(fields.get(1).getStringValue());
                entityIdAndSumMap.merge(entityId, value, Double::sum);
                entityIdAndCountMap.merge(entityId, 1, Integer::sum);
            }

            entityIdAndSumMap.forEach((String entityId, Double sum) -> {
                double count = entityIdAndCountMap.get(entityId);
                double avg = sum / count;
                SelectedRecord topNEntity = new SelectedRecord();
                topNEntity.setId(entityId);
                topNEntity.setValue(String.valueOf((long) avg));
                topEntities.add(topNEntity);
            });
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e);
        } finally {
            if (wrapper != null) {
                sessionPool.closeResultSet(wrapper);
            }
        }

        if (condition.getOrder().equals(Order.DES)) {
            topEntities.sort((SelectedRecord t1, SelectedRecord t2) ->
                    Double.compare(Double.parseDouble(t2.getValue()), Double.parseDouble(t1.getValue())));
        } else {
            topEntities.sort(Comparator.comparingDouble((SelectedRecord t) -> Double.parseDouble(t.getValue())));
        }
        int limit = condition.getTopN();
        return limit > topEntities.size() ? topEntities : topEntities.subList(0, limit);
    }
}
