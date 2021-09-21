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
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
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

public class IoTDBProfileTaskQueryDAOTest {
    private IoTDBProfileTaskQueryDAO profileTaskQueryDAO;

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

        profileTaskQueryDAO = new IoTDBProfileTaskQueryDAO(client);

        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(ProfileTaskRecord.class, ProfileTaskRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model profileTaskRecordModel = new Model(
                ProfileTaskRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(ProfileTaskRecord.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(profileTaskRecordModel);

        StorageHashMapBuilder<ProfileTaskRecord> profileTaskRecordBuilder = new ProfileTaskRecord.Builder();
        Map<String, Object> profileTaskRecordMap = new HashMap<>();
        profileTaskRecordMap.put(ProfileTaskRecord.SERVICE_ID, "service_id_1");
        profileTaskRecordMap.put(ProfileTaskRecord.ENDPOINT_NAME, "endpoint_name_1");
        profileTaskRecordMap.put(ProfileTaskRecord.START_TIME, 1L);
        profileTaskRecordMap.put(ProfileTaskRecord.DURATION, 2);
        profileTaskRecordMap.put(ProfileTaskRecord.MIN_DURATION_THRESHOLD, 3);
        profileTaskRecordMap.put(ProfileTaskRecord.DUMP_PERIOD, 4);
        profileTaskRecordMap.put(ProfileTaskRecord.CREATE_TIME, 5L);
        profileTaskRecordMap.put(ProfileTaskRecord.MAX_SAMPLING_COUNT, 6);
        profileTaskRecordMap.put(ProfileTaskRecord.TIME_BUCKET, 7L);
        ProfileTaskRecord profileTaskRecord = profileTaskRecordBuilder.storage2Entity(profileTaskRecordMap);

        IoTDBInsertRequest request = new IoTDBInsertRequest(ProfileTaskRecord.INDEX_NAME, 1L, profileTaskRecord, profileTaskRecordBuilder);
        client.write(request);
    }

    @Test
    public void getTaskList() throws IOException {
        List<ProfileTask> tasks = profileTaskQueryDAO.getTaskList("service_id_1", "endpoint_name_1",
                null, null, null);
        ProfileTask profileTask = tasks.get(0);
        assertThat(profileTask.getServiceId()).isEqualTo("service_id_1");
        assertThat(profileTask.getEndpointName()).isEqualTo("endpoint_name_1");
    }

    @Test
    public void getById() throws IOException {
        ProfileTask profileTask = profileTaskQueryDAO.getById("5_service_id_1");
        assertThat(profileTask.getServiceId()).isEqualTo("service_id_1");
        assertThat(profileTask.getEndpointName()).isEqualTo("endpoint_name_1");
    }
}