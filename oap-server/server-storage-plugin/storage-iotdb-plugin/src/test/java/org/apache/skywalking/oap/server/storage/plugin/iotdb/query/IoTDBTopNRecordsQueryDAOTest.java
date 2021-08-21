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
import java.util.List;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBStorageConfig;
import org.junit.Before;
import org.junit.Test;

public class IoTDBTopNRecordsQueryDAOTest {
    IoTDBTopNRecordsQueryDAO topNRecordsQueryDAO;
    private String modelName = "topN_test";

    @Before
    public void setUp() throws Exception {
        IoTDBStorageConfig config = new IoTDBStorageConfig();
        config.setHost("127.0.0.1");
        config.setRpcPort(6667);
        config.setUsername("root");
        config.setPassword("root");
        config.setStorageGroup("root.skywalking");
        config.setSessionPoolSize(3);
        config.setFetchTaskLogMaxSize(1000);

        IoTDBClient client = new IoTDBClient(config);
        client.connect();

        topNRecordsQueryDAO = new IoTDBTopNRecordsQueryDAO(client);

        SessionPool sessionPool = client.getSessionPool();

        // TODO add IoTDBTableMetaInfo, otherwise this test case cannot run.
        String serviceId = "\"" + IDManager.ServiceID.buildId("service_id_4", true) + "\"";
        List<String> measurements = new ArrayList<>();
        measurements.add(TopN.STATEMENT);
        measurements.add(TopN.TRACE_ID);
        measurements.add("valueColumnName");
        List<TSDataType> types = new ArrayList<>();
        types.add(TSDataType.TEXT);
        types.add(TSDataType.TEXT);
        types.add(TSDataType.INT64);
        List<Object> values1 = new ArrayList<>();
        values1.add("statement_1");
        values1.add("trace_id_1");
        values1.add(1L);
        List<Object> values2 = new ArrayList<>();
        values2.add("statement_2");
        values2.add("trace_id_2");
        values2.add(2L);
        sessionPool.insertRecord("root.skywalking." + modelName + ".id_1." + serviceId,
                TimeBucket.getTimestamp(10000000000001L), measurements, types, values1);
        sessionPool.insertRecord("root.skywalking." + modelName + ".id_2." + serviceId,
                TimeBucket.getTimestamp(10000000000002L), measurements, types, values2);
    }

    @Test
    public void readSampledRecords() throws IOException {
        TopNCondition condition = new TopNCondition();
        condition.setName(modelName);
        condition.setParentService("service_id_4");
        condition.setNormal(true);
        condition.setTopN(10);
        condition.setOrder(Order.DES);
        Duration duration = null;
        List<SelectedRecord> selectedRecordList = topNRecordsQueryDAO.readSampledRecords(condition, InstanceTraffic.LAST_PING_TIME_BUCKET, duration);
        long lastPing = Long.MAX_VALUE;
        for (SelectedRecord record : selectedRecordList) {
            assert Long.parseLong(record.getValue()) < lastPing;
            lastPing = Long.parseLong(record.getValue());
        }
    }
}