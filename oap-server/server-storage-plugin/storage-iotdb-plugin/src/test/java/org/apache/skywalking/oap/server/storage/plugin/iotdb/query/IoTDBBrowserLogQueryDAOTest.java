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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.ExtraQueryIndex;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBInsertRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClientTest.retrieval;

public class IoTDBBrowserLogQueryDAOTest {
    private IoTDBBrowserLogQueryDAO browserLogQueryDAO;

    @Rule
    public GenericContainer iotdb = new GenericContainer(DockerImageName.parse("apache/iotdb:0.12.2-node")).withExposedPorts(6667);

    @Before
    public void setUp() throws Exception {
        IoTDBStorageConfig config = new IoTDBStorageConfig();
        config.setHost(iotdb.getHost());
        config.setRpcPort(iotdb.getFirstMappedPort());
        config.setUsername("root");
        config.setPassword("root");
        config.setStorageGroup("root.skywalking");
        config.setSessionPoolSize(3);
        config.setFetchTaskLogMaxSize(1000);

        IoTDBClient client = new IoTDBClient(config);
        client.connect();

        browserLogQueryDAO = new IoTDBBrowserLogQueryDAO(client);

        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(BrowserErrorLogRecord.class, BrowserErrorLogRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model browserErrorLogRecordModel = new Model(
                BrowserErrorLogRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(BrowserErrorLogRecord.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(browserErrorLogRecordModel);

        StorageHashMapBuilder<BrowserErrorLogRecord> browserErrorLogRecordBuilder = new BrowserErrorLogRecord.Builder();
        Map<String, Object> browserErrorLogRecordMap = new HashMap<>();
        browserErrorLogRecordMap.put(BrowserErrorLogRecord.UNIQUE_ID, "unique_id_1");
        browserErrorLogRecordMap.put(BrowserErrorLogRecord.SERVICE_ID, "service_id_1");
        browserErrorLogRecordMap.put(BrowserErrorLogRecord.SERVICE_VERSION_ID, "0.1");
        browserErrorLogRecordMap.put(BrowserErrorLogRecord.PAGE_PATH_ID, "path_id_1");
        browserErrorLogRecordMap.put(BrowserErrorLogRecord.TIMESTAMP, 1L);
        browserErrorLogRecordMap.put(BrowserErrorLogRecord.ERROR_CATEGORY, BrowserErrorCategory.AJAX.getValue());
        // TODO the DATA_BINARY value is not a viable test case
        browserErrorLogRecordMap.put(BrowserErrorLogRecord.DATA_BINARY, "ZGF0YV9iaW5hcnlfMQ==");
        browserErrorLogRecordMap.put(BrowserErrorLogRecord.TIME_BUCKET, 2L);
        BrowserErrorLogRecord browserErrorLogRecord1 = browserErrorLogRecordBuilder.storage2Entity(browserErrorLogRecordMap);

        long timestamp1 = TimeBucket.getTimestamp(10000000000001L);
        IoTDBInsertRequest request = new IoTDBInsertRequest(BrowserErrorLogRecord.INDEX_NAME, timestamp1, browserErrorLogRecord1, browserErrorLogRecordBuilder);
        client.write(request);

        browserErrorLogRecordMap.put(BrowserErrorLogRecord.UNIQUE_ID, "unique_id_2");
        browserErrorLogRecordMap.put(BrowserErrorLogRecord.TIMESTAMP, 2L);
        // TODO the DATA_BINARY is not a viable test case
        browserErrorLogRecordMap.put(BrowserErrorLogRecord.DATA_BINARY, "ZGF0YV9iaW5hcnlfMg==");
        BrowserErrorLogRecord browserErrorLogRecord2 = browserErrorLogRecordBuilder.storage2Entity(browserErrorLogRecordMap);
        long timestamp2 = TimeBucket.getTimestamp(10000000000002L);
        request = new IoTDBInsertRequest(BrowserErrorLogRecord.INDEX_NAME, timestamp2, browserErrorLogRecord2, browserErrorLogRecordBuilder);
        client.write(request);
    }

    @Test
    public void queryBrowserErrorLogs() throws IOException {
//        BrowserErrorLogs browserErrorLogs = browserLogQueryDAO.queryBrowserErrorLogs("service_id_1", "0.1", "path_id_1",
//                BrowserErrorCategory.AJAX, 10000000000001L, 10000000000002L, 10, 0);
//        List<BrowserErrorLog> logs = browserErrorLogs.getLogs();
//        logs.forEach(browserErrorLog -> {
//            assert browserErrorLog.getService().equals("service_id_1");
//            assert browserErrorLog.getServiceVersion().equals("0.1");
//            assert browserErrorLog.getPagePath().equals("path_id_1");
//            assert browserErrorLog.getCategory() == ErrorCategory.AJAX;
//        });
    }
}