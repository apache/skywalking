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
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.indicator.IntKeyLongValue;
import org.apache.skywalking.oap.server.core.analysis.indicator.IntKeyLongValueArray;
import org.apache.skywalking.oap.server.core.analysis.indicator.ThermodynamicIndicator;
import org.apache.skywalking.oap.server.core.query.entity.IntValues;
import org.apache.skywalking.oap.server.core.query.entity.KVInt;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.apache.skywalking.oap.server.core.query.entity.Thermodynamic;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.sql.KeyValues;
import org.apache.skywalking.oap.server.core.query.sql.Where;
import org.apache.skywalking.oap.server.core.storage.DownSamplingModelNameBuilder;
import org.apache.skywalking.oap.server.core.storage.query.IMetricQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

/**
 * @author wusheng
 */
public class H2MetricQueryDAO extends H2SQLExecutor implements IMetricQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2MetricQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public IntValues getValues(String indName, Step step, long startTB, long endTB, Where where, String valueCName,
        Function function) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, indName);

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
                whereSql.append(keyValues.getKey()).append(" in (" + valueCollection + ")");
            }
            whereSql.append(") and ");
        }

        IntValues intValues = new IntValues();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, "select " + Indicator.ENTITY_ID + " id, " + op + "(" + valueCName + ") value from " + tableName
                    + " where " + whereSql
                    + Indicator.TIME_BUCKET + ">= ? and " + Indicator.TIME_BUCKET + "<=?"
                    + " group by " + Indicator.ENTITY_ID,
                startTB, endTB)) {

                while (resultSet.next()) {
                    KVInt kv = new KVInt();
                    kv.setId(resultSet.getString("id"));
                    kv.setValue(resultSet.getLong("value"));
                    intValues.getValues().add(kv);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return orderWithDefault0(intValues, ids);
    }

    @Override public IntValues getLinearIntValues(String indName, Step step, List<String> ids,
        String valueCName) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, indName);

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
                    intValues.getValues().add(kv);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return orderWithDefault0(intValues, ids);
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

    @Override public Thermodynamic getThermodynamic(String indName, Step step, List<String> ids,
        String valueCName) throws IOException {
        String tableName = DownSamplingModelNameBuilder.build(step, indName);

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
            try (ResultSet resultSet = h2Client.executeQuery(connection, "select " + ThermodynamicIndicator.STEP + " step, "
                + ThermodynamicIndicator.NUM_OF_STEPS + " num_of_steps, "
                + ThermodynamicIndicator.DETAIL_GROUP + " detail_group, "
                + "id "
                + " from " + tableName + " where id in (" + idValues.toString() + ")")) {


                while (resultSet.next()) {
                    axisYStep = resultSet.getInt("step");
                    String id = resultSet.getString("id");
                    numOfSteps = resultSet.getInt("num_of_steps") + 1;
                    String value = resultSet.getString("detail_group");
                    IntKeyLongValueArray intKeyLongValues = new IntKeyLongValueArray(5);
                    intKeyLongValues.toObject(value);

                    List<Long> axisYValues = new ArrayList<>();
                    for (int i = 0; i < numOfSteps; i++) {
                        axisYValues.add(0L);
                    }

                    for (IntKeyLongValue intKeyLongValue : intKeyLongValues) {
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
