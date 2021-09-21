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

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.ExtraQueryIndex;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBInsertRequest;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBStorageDAO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClientTest.retrieval;
import static org.assertj.core.api.Assertions.assertThat;

public class IoTDBAlarmQueryDAOTest {
    private IoTDBAlarmQueryDAO alarmQueryDAO;

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

        alarmQueryDAO = new IoTDBAlarmQueryDAO(client);

        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(AlarmRecord.class, AlarmRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model alarmRecordModel = new Model(
                AlarmRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(AlarmRecord.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(alarmRecordModel);

        StorageHashMapBuilder<AlarmRecord> alarmRecordBuilder = new AlarmRecord.Builder();
        Map<String, Object> alarmRecordMap = new HashMap<>();
        alarmRecordMap.put(AlarmRecord.SCOPE, Scope.Service.getScopeId());
        alarmRecordMap.put(AlarmRecord.NAME, "name_1");
        alarmRecordMap.put(AlarmRecord.ID0, "id0_1");
        alarmRecordMap.put(AlarmRecord.ID1, "id1_1");
        alarmRecordMap.put(AlarmRecord.START_TIME, 1L);
        alarmRecordMap.put(AlarmRecord.ALARM_MESSAGE, "message_1");
        alarmRecordMap.put(AlarmRecord.RULE_NAME, "rule_1");
        alarmRecordMap.put(AlarmRecord.TIME_BUCKET, 10000000000001L);
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag("tag1", "value1"));
        tags.add(new Tag("tag2", "value2"));
        Gson gson = new Gson();
        AlarmRecord alarmRecord1 = alarmRecordBuilder.storage2Entity(alarmRecordMap);
        alarmRecord1.setTags(tags);
        alarmRecord1.setTagsInString(Tag.Util.toStringList(tags));
        alarmRecord1.setTagsRawData(gson.toJson(tags).getBytes(Charsets.UTF_8));

        IRecordDAO recordDAO = new IoTDBStorageDAO(client).newRecordDao(alarmRecordBuilder);
        IoTDBInsertRequest request = (IoTDBInsertRequest) recordDAO.prepareBatchInsert(alarmRecordModel, alarmRecord1);
        client.write(request);

        alarmRecordMap.put(AlarmRecord.NAME, "name_2");
        alarmRecordMap.put(AlarmRecord.ID0, "id0_2");
        alarmRecordMap.put(AlarmRecord.ID1, "id1_2");
        alarmRecordMap.put(AlarmRecord.START_TIME, 2L);
        alarmRecordMap.put(AlarmRecord.TIME_BUCKET, 10000000000002L);
        AlarmRecord alarmRecord2 = alarmRecordBuilder.storage2Entity(alarmRecordMap);
        alarmRecord2.setTags(tags);
        alarmRecord2.setTagsInString(Tag.Util.toStringList(tags));
        alarmRecord2.setTagsRawData(gson.toJson(tags).getBytes(Charsets.UTF_8));
        request = (IoTDBInsertRequest) recordDAO.prepareBatchInsert(alarmRecordModel, alarmRecord2);
        client.write(request);
    }

    @Test
    public void getAlarm() throws IOException {
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag("tag1", "value1"));
        tags.add(new Tag("tag2", "value2"));
        Alarms alarms = alarmQueryDAO.getAlarm(Scope.Service.getScopeId(), "", 100, 0,
                10000000000001L, 10000000000002L, tags);
        List<AlarmMessage> messageList = alarms.getMsgs();
        long startTime = Long.MAX_VALUE;
        for (AlarmMessage message : messageList) {
            assertThat(message.getScope().getScopeId()).isEqualTo(Scope.Service.getScopeId());
            assertThat(message.getStartTime()).isLessThanOrEqualTo(startTime);
            startTime = message.getStartTime();
        }

        alarms = alarmQueryDAO.getAlarm(Scope.Service.getScopeId(), "1", 100, 0,
                10000000000001L, 10000000000002L, tags);
        messageList = alarms.getMsgs();
        startTime = Long.MAX_VALUE;
        for (AlarmMessage message : messageList) {
            assertThat(message.getScope().getScopeId()).isEqualTo(Scope.Service.getScopeId());
            assertThat(message.getMessage()).contains("1");
            assertThat(message.getStartTime()).isLessThanOrEqualTo(startTime);
            startTime = message.getStartTime();
        }
    }
}