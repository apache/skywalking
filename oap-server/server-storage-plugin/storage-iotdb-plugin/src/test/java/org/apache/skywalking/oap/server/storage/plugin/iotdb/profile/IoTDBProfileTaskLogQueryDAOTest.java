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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.profile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
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
import static org.assertj.core.api.Assertions.assertThat;

public class IoTDBProfileTaskLogQueryDAOTest {
    private IoTDBProfileTaskLogQueryDAO profileTaskLogQueryDAO;

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

        profileTaskLogQueryDAO = new IoTDBProfileTaskLogQueryDAO(client, 1000);

        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(ProfileTaskLogRecord.class, ProfileTaskLogRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model profileTaskLogRecordModel = new Model(
                ProfileTaskLogRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(ProfileTaskLogRecord.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(profileTaskLogRecordModel);

        StorageHashMapBuilder<ProfileTaskLogRecord> profileTaskLogBuilder = new ProfileTaskLogRecord.Builder();
        Map<String, Object> profileTaskLogRecordMap = new HashMap<>();
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.TASK_ID, "task_id_1");
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.INSTANCE_ID, "instance_id_1");
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.OPERATION_TYPE, 1);
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.OPERATION_TIME, 2L);
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.TIME_BUCKET, 3L);
        ProfileTaskLogRecord profileTaskLogRecord1 = profileTaskLogBuilder.storage2Entity(profileTaskLogRecordMap);
        IoTDBInsertRequest request = new IoTDBInsertRequest(ProfileTaskLogRecord.INDEX_NAME, 3L, profileTaskLogRecord1, profileTaskLogBuilder);
        client.write(request);

        profileTaskLogRecordMap.put(ProfileTaskLogRecord.TASK_ID, "task_id_2");
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.INSTANCE_ID, "instance_id_2");
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.OPERATION_TYPE, 1);
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.OPERATION_TIME, 3L);
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.TIME_BUCKET, 3L);
        ProfileTaskLogRecord profileTaskLogRecord2 = profileTaskLogBuilder.storage2Entity(profileTaskLogRecordMap);
        request = new IoTDBInsertRequest(ProfileTaskLogRecord.INDEX_NAME, 1L, profileTaskLogRecord2, profileTaskLogBuilder);
        client.write(request);
    }

    @Test
    public void getTaskLogList() throws IOException {
        List<ProfileTaskLog> profileTaskLogList = profileTaskLogQueryDAO.getTaskLogList();
        long operationTime = Long.MAX_VALUE;
        for (ProfileTaskLog profileTaskLog : profileTaskLogList) {
            long logOperationTime = profileTaskLog.getOperationTime();
            assertThat(logOperationTime).isLessThanOrEqualTo(operationTime);
            operationTime = logOperationTime;
        }
    }
}