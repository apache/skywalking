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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValue;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValueHashMap;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.ThermodynamicMetrics;
import org.apache.skywalking.oap.server.core.query.entity.IntValues;
import org.apache.skywalking.oap.server.core.query.entity.KVInt;
import org.apache.skywalking.oap.server.core.query.entity.Thermodynamic;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.sql.KeyValues;
import org.apache.skywalking.oap.server.core.query.sql.Where;
import org.apache.skywalking.oap.server.core.storage.model.ModelName;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

/**
 * @author wusheng
 */
public class H2MetricsQueryDAO extends H2SQLExecutor implements IMetricsQueryDAO {

    private JDBCHikariCPClient h2Client;

    public H2MetricsQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public IntValues getValues(String indName, Downsampling downsampling, long startTB, long endTB, Where where,
        String valueCName,
        Function function) throws IOException {
        String tableName = ModelName.build(downsampling, indName);

        List<KeyValues> whereKeyValues = where.getKeyValues();
        String op;
        switch (function) {
            case Avg:
                op = "avg";
                break;
            default:
                op = "sum";
        }
        List<String> ids = new ArrayList<>(20);
        StringBuilder whereSql = new StringBuilder();
        if (whereKeyValues.size() > 0) {
            whereSql.append("(");
            for (int i = 0; i < whereKeyValues.size(); i++) {
                if (i != 0) {
                    whereSql.append(" or ");
                }
                KeyValues keyValues = whereKeyValues.get(i);

                StringBuilder valueCollection = new StringBuilder();
                List<String> values = keyValues.getValues();
                for (int valueIdx = 0; valueIdx < values.size(); valueIdx++) {
                    if (valueIdx != 0) {
                        valueCollection.append(",");
                    }
                    String id = values.get(valueIdx);
                    ids.add(id);
                    valueCollection.append("'").append(id).append("'");
                }
                whereSql.append(keyValues.getKey()).append(" in (").append(valueCollection).append(")");
            }
            whereSql.append(") and ");
        }

        IntValues intValues = new IntValues();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, "select " + Metrics.ENTITY_ID + " id, " + op + "(" + valueCName + ") value from " + tableName
                    + " where " + whereSql
                    + Metrics.TIME_BUCKET + ">= ? and " + Metrics.TIME_BUCKET + "<=?"
                    + " group by " + Metrics.ENTITY_ID,
                startTB, endTB)) {

                while (resultSet.next()) {
                    KVInt kv = new KVInt();
                    kv.setId(resultSet.getString("id"));
                    kv.setValue(resultSet.getLong("value"));
                    intValues.addKVInt(kv);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return orderWithDefault0(intValues, ids);
    }

    @Override public IntValues getLinearIntValues(String indName, Downsampling downsampling, List<String> ids,
        String valueCName) throws IOException {
        String tableName = ModelName.build(downsampling, indName);

        StringBuilder idValues = new StringBuilder();
        for (int valueIdx = 0; valueIdx < ids.size(); valueIdx++) {
            if (valueIdx != 0) {
                idValues.append(",");
            }
            idValues.append("'").append(ids.get(valueIdx)).append("'");
        }

        IntValues intValues = new IntValues();

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, "select id, " + valueCName + " from " + tableName + " where id in (" + idValues.toString() + ")")) {
                while (resultSet.next()) {
                    KVInt kv = new KVInt();
                    kv.setId(resultSet.getString("id"));
                    kv.setValue(resultSet.getLong(valueCName));
                    intValues.addKVInt(kv);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        return orderWithDefault0(intValues, ids);
    }

    @Override public IntValues[] getMultipleLinearIntValues(String indName, Downsampling downsampling,
        List<String> ids,
        final List<Integer> linearIndex,
        String valueCName) throws IOException {
        String tableName = ModelName.build(downsampling, indName);

        StringBuilder idValues = new StringBuilder();
        for (int valueIdx = 0; valueIdx < ids.size(); valueIdx++) {
            if (valueIdx != 0) {
                idValues.append(",");
            }
            idValues.append("'").append(ids.get(valueIdx)).append("'");
        }

        IntValues[] intValuesArray = new IntValues[linearIndex.size()];
        for (int i = 0; i < intValuesArray.length; i++) {
            intValuesArray[i] = new IntValues();
        }

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, "select id, " + valueCName + " from " + tableName + " where id in (" + idValues.toString() + ")")) {
                while (resultSet.next()) {
                    String id = resultSet.getString("id");

                    IntKeyLongValueHashMap multipleValues = new IntKeyLongValueHashMap(5);
                    multipleValues.toObject(resultSet.getString(valueCName));

                    for (int i = 0; i < linearIndex.size(); i++) {
                        Integer index = linearIndex.get(i);
                        KVInt kv = new KVInt();
                        kv.setId(id);
                        kv.setValue(multipleValues.get(index).getValue());
                        intValuesArray[i].addKVInt(kv);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        return orderWithDefault0(intValuesArray, ids);
    }

    /**
     * Make sure the order is same as the expected order, and keep default value as 0.
     *
     * @param origin
     * @param expectedOrder
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

    /**
     * Make sure the order is same as the expected order, and keep default value as 0.
     *
     * @param origin
     * @param expectedOrder
     * @return
     */
    private IntValues[] orderWithDefault0(IntValues[] origin, List<String> expectedOrder) {
        for (int i = 0; i < origin.length; i++) {
            origin[i] = orderWithDefault0(origin[i], expectedOrder);
        }
        return origin;
    }

    @Override public Thermodynamic getThermodynamic(String indName, Downsampling downsampling, List<String> ids,
        String valueCName) throws IOException {
        String tableName = ModelName.build(downsampling, indName);

        StringBuilder idValues = new StringBuilder();
        for (int valueIdx = 0; valueIdx < ids.size(); valueIdx++) {
            if (valueIdx != 0) {
                idValues.append(",");
            }
            idValues.append("'").append(ids.get(valueIdx)).append("'");
        }

        List<List<Long>> thermodynamicValueCollection = new ArrayList<>();
        Map<String, List<Long>> thermodynamicValueMatrix = new HashMap<>();

        try (Connection connection = h2Client.getConnection()) {
            Thermodynamic thermodynamic = new Thermodynamic();
            int numOfSteps = 0;
            int axisYStep = 0;
            try (ResultSet resultSet = h2Client.executeQuery(connection, "select " + ThermodynamicMetrics.STEP + " step, "
                + ThermodynamicMetrics.NUM_OF_STEPS + " num_of_steps, "
                + ThermodynamicMetrics.DETAIL_GROUP + " detail_group, "
                + "id "
                + " from " + tableName + " where id in (" + idValues.toString() + ")")) {

                while (resultSet.next()) {
                    axisYStep = resultSet.getInt("step");
                    String id = resultSet.getString("id");
                    numOfSteps = resultSet.getInt("num_of_steps") + 1;
                    String value = resultSet.getString("detail_group");
                    IntKeyLongValueHashMap intKeyLongValues = new IntKeyLongValueHashMap(5);
                    intKeyLongValues.toObject(value);

                    List<Long> axisYValues = new ArrayList<>();
                    for (int i = 0; i < numOfSteps; i++) {
                        axisYValues.add(0L);
                    }

                    for (IntKeyLongValue intKeyLongValue : intKeyLongValues.values()) {
                        axisYValues.set(intKeyLongValue.getKey(), intKeyLongValue.getValue());
                    }

                    thermodynamicValueMatrix.put(id, axisYValues);
                }

                // try to add default values when there is no data in that time bucket.
                ids.forEach(id -> {
                    if (thermodynamicValueMatrix.containsKey(id)) {
                        thermodynamicValueCollection.add(thermodynamicValueMatrix.get(id));
                    } else {
                        thermodynamicValueCollection.add(new ArrayList<>());
                    }
                });
            }

            thermodynamic.fromMatrixData(thermodynamicValueCollection, numOfSteps);
            thermodynamic.setAxisYStep(axisYStep);

            return thermodynamic;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}
