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

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValue;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValueHashMap;
import org.apache.skywalking.oap.server.core.analysis.metrics.ThermodynamicMetrics;
import org.apache.skywalking.oap.server.core.query.entity.IntValues;
import org.apache.skywalking.oap.server.core.query.entity.KVInt;
import org.apache.skywalking.oap.server.core.query.entity.Thermodynamic;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.sql.KeyValues;
import org.apache.skywalking.oap.server.core.query.sql.Where;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.ModelName;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.base.MetricsDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.SelectionQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class MetricsQuery implements IMetricsQueryDAO {
    private final InfluxClient client;

    public MetricsQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public IntValues getValues(String indName, Downsampling downsampling, long startTB, long endTB,
                               Where where, String valueCName, Function function) throws IOException {
        String measurement = ModelName.build(downsampling, indName);

        SelectionQueryImpl query = select();
        switch (function) {
            case Avg:
                query.mean(valueCName);
                break;
            default:
                query.sum(valueCName);
        }
        WhereQueryImpl<SelectQueryImpl> queryWhereQuery = query.from(client.getDatabase(), measurement).where();

        Map<String, Class<?>> columnTypes = Maps.newHashMap();
        for (ModelColumn column : TableMetaInfo.get(measurement).getColumns()) {
            columnTypes.put(column.getColumnName().getStorageName(), column.getType());
        }

        List<String> ids = new ArrayList<>(20);
        List<KeyValues> whereKeyValues = where.getKeyValues();
        if (!whereKeyValues.isEmpty()) {
            StringBuilder clauseBuilder = new StringBuilder();
            for (KeyValues kv : whereKeyValues) {
                final List<String> values = kv.getValues();

                Class<?> type = columnTypes.get(kv.getKey());
                if (values.size() == 1) {
                    String value = kv.getValues().get(0);
                    if (type == String.class) {
                        value = "'" + value + "'";
                    }
                    clauseBuilder.append(kv.getKey()).append("=").append(value).append(" OR ");
                } else {
                    ids.addAll(values);
                    if (type == String.class) {
                        clauseBuilder.append(kv.getKey())
                                     .append(" =~ /")
                                     .append(Joiner.on("|").join(values))
                                     .append("/ OR ");
                        continue;
                    }
                    for (String value : values) {
                        clauseBuilder.append(kv.getKey()).append(" = '").append(value).append("' OR ");
                    }
                }
            }
            queryWhereQuery.where(clauseBuilder.substring(0, clauseBuilder.length() - 4));
        }
        queryWhereQuery
            .and(gte(InfluxClient.TIME, InfluxClient.timeInterval(startTB, downsampling)))
            .and(lte(InfluxClient.TIME, InfluxClient.timeInterval(endTB, downsampling)))
            .groupBy(MetricsDAO.TAG_ENTITY_ID);

        IntValues intValues = new IntValues();
        List<QueryResult.Series> seriesList = client.queryForSeries(queryWhereQuery);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", queryWhereQuery.getCommand(), seriesList);
        }
        if (!(seriesList == null || seriesList.isEmpty())) {
            for (QueryResult.Series series : seriesList) {
                KVInt kv = new KVInt();
                kv.setId(series.getTags().get(MetricsDAO.TAG_ENTITY_ID));
                Number value = (Number) series.getValues().get(0).get(1);
                kv.setValue(value.longValue());

                intValues.addKVInt(kv);
            }
        }

        return orderWithDefault0(intValues, ids);
    }

    @Override
    public IntValues getLinearIntValues(String indName, Downsampling downsampling, List<String> ids, String valueCName)
        throws IOException {
        String measurement = ModelName.build(downsampling, indName);

        WhereQueryImpl<SelectQueryImpl> query = select()
            .column("id")
            .column(valueCName)
            .from(client.getDatabase(), measurement)
            .where();

        if (ids != null && !ids.isEmpty()) {
            if (ids.size() == 1) {
                query.where(eq("id", ids.get(0)));
            } else {
                query.where(contains("id", Joiner.on("|").join(ids)));
            }
        }
        List<QueryResult.Series> seriesList = client.queryForSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), seriesList);
        }

        IntValues intValues = new IntValues();
        if (!(seriesList == null || seriesList.isEmpty())) {
            seriesList.get(0).getValues().forEach(values -> {
                KVInt kv = new KVInt();
                kv.setValue(((Number) values.get(2)).longValue());
                kv.setId((String) values.get(1));
                intValues.addKVInt(kv);
            });
        }
        return orderWithDefault0(intValues, ids);
    }

    /**
     * Make sure the order is same as the expected order, and keep default value as 0.
     *
     * @param origin        IntValues
     * @param expectedOrder List
     * @return
     */
    private IntValues orderWithDefault0(IntValues origin, List<String> expectedOrder) {
        IntValues intValues = new IntValues();

        expectedOrder.forEach(id -> {
            KVInt e = new KVInt();
            e.setId(id);
            e.setValue(origin.findValue(id, 0));
            intValues.addKVInt(e);
        });

        return intValues;
    }

    @Override
    public IntValues[] getMultipleLinearIntValues(String indName, Downsampling downsampling, List<String> ids,
                                                  List<Integer> linearIndex, String valueCName) throws IOException {
        String measurement = ModelName.build(downsampling, indName);

        WhereQueryImpl<SelectQueryImpl> query = select()
            .column("id")
            .column(valueCName)
            .from(client.getDatabase(), measurement)
            .where();

        if (ids != null && !ids.isEmpty()) {
            if (ids.size() == 1) {
                query.where(eq("id", ids.get(0)));
            } else {
                query.where(contains("id", Joiner.on("|").join(ids)));
            }
        }
        List<QueryResult.Series> series = client.queryForSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        IntValues[] intValues = new IntValues[linearIndex.size()];
        for (int i = 0; i < intValues.length; i++) {
            intValues[i] = new IntValues();
        }
        if (series == null || series.isEmpty()) {
            return intValues;
        }
        series.get(0).getValues().forEach(values -> {
            IntKeyLongValueHashMap multipleValues = new IntKeyLongValueHashMap(5);
            multipleValues.toObject((String) values.get(2));

            final String id = (String) values.get(1);
            for (int i = 0; i < intValues.length; i++) {
                Integer index = linearIndex.get(i);
                KVInt kv = new KVInt();
                kv.setId(id);
                kv.setValue(multipleValues.get(index).getValue());
                intValues[i].addKVInt(kv);
            }
        });
        return orderWithDefault0(intValues, ids);
    }

    /**
     * Make sure the order is same as the expected order, and keep default value as 0.
     *
     * @param origin        IntValues[]
     * @param expectedOrder List
     * @return
     */
    private IntValues[] orderWithDefault0(IntValues[] origin, List<String> expectedOrder) {
        for (int i = 0; i < origin.length; i++) {
            origin[i] = orderWithDefault0(origin[i], expectedOrder);
        }
        return origin;
    }

    @Override
    public Thermodynamic getThermodynamic(String indName, Downsampling downsampling, List<String> ids,
                                          String valueCName)
        throws IOException {
        String measurement = ModelName.build(downsampling, indName);
        WhereQueryImpl<SelectQueryImpl> query = select()
            .column(ThermodynamicMetrics.STEP)
            .column(ThermodynamicMetrics.NUM_OF_STEPS)
            .column(ThermodynamicMetrics.DETAIL_GROUP)
            .column("id")
            .from(client.getDatabase(), measurement)
            .where(contains("id", Joiner.on("|").join(ids)));
        Map<String, List<Long>> thermodynamicValueMatrix = new HashMap<>();

        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null) {
            return new Thermodynamic();
        }

        int numOfSteps = 0, axisYStep = 0;
        List<List<Long>> thermodynamicValueCollection = new ArrayList<>();
        Thermodynamic thermodynamic = new Thermodynamic();
        for (List<Object> values : series.getValues()) {
            numOfSteps = (int) values.get(2) + 1;
            axisYStep = (int) values.get(1);
            IntKeyLongValueHashMap intKeyLongValues = new IntKeyLongValueHashMap(5);
            intKeyLongValues.toObject((String) values.get(3));
            List<Long> axisYValues = new ArrayList<>(numOfSteps);
            for (int i = 0; i < numOfSteps; i++) {
                axisYValues.add(0L);
            }
            for (IntKeyLongValue intKeyLongValue : intKeyLongValues.values()) {
                axisYValues.set(intKeyLongValue.getKey(), intKeyLongValue.getValue());
            }
            thermodynamicValueMatrix.put((String) values.get(4), axisYValues);
        }
        // try to add default values when there is no data in that time bucket.
        ids.forEach(id -> {
            if (thermodynamicValueMatrix.containsKey(id)) {
                thermodynamicValueCollection.add(thermodynamicValueMatrix.get(id));
            } else {
                thermodynamicValueCollection.add(new ArrayList<>());
            }
        });
        thermodynamic.fromMatrixData(thermodynamicValueCollection, numOfSteps);
        thermodynamic.setAxisYStep(axisYStep);

        return thermodynamic;
    }
}