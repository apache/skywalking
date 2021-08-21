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
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
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
import org.junit.Test;

import static org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClientTest.retrieval;

public class IoTDBProfileThreadSnapshotQueryDAOTest {
    private IoTDBProfileThreadSnapshotQueryDAO profileThreadSnapshotQueryDAO;

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

        profileThreadSnapshotQueryDAO = new IoTDBProfileThreadSnapshotQueryDAO(client);

        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(ProfileThreadSnapshotRecord.class, ProfileThreadSnapshotRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model profileThreadSnapshotRecordModel = new Model(
                ProfileThreadSnapshotRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(ProfileThreadSnapshotRecord.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(profileThreadSnapshotRecordModel);

        scopeId = 1;
        record = false;
        superDataset = false;
        timeRelativeID = true;
        modelColumns = new ArrayList<>();
        extraQueryIndices = new ArrayList<>();
        retrieval(SegmentRecord.class, SegmentRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model segmentRecordModel = new Model(
                SegmentRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(SegmentRecord.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(segmentRecordModel);

        StorageHashMapBuilder<ProfileThreadSnapshotRecord> profileThreadSnapshotRecordBuilder = new ProfileThreadSnapshotRecord.Builder();
        Map<String, Object> profileThreadSnapshotRecordMap1 = new HashMap<>();
        profileThreadSnapshotRecordMap1.put(ProfileThreadSnapshotRecord.TASK_ID, "task_id_1");
        profileThreadSnapshotRecordMap1.put(ProfileThreadSnapshotRecord.SEGMENT_ID, "segment_id_1");
        profileThreadSnapshotRecordMap1.put(ProfileThreadSnapshotRecord.DUMP_TIME, 1L);
        profileThreadSnapshotRecordMap1.put(ProfileThreadSnapshotRecord.SEQUENCE, 2);
        profileThreadSnapshotRecordMap1.put(ProfileThreadSnapshotRecord.STACK_BINARY, "U1RBQ0tfQklOQVJZ");
        profileThreadSnapshotRecordMap1.put(ProfileThreadSnapshotRecord.TIME_BUCKET, 3L);
        ProfileThreadSnapshotRecord profileThreadSnapshotRecord1 = profileThreadSnapshotRecordBuilder.storage2Entity(profileThreadSnapshotRecordMap1);

        Map<String, Object> profileThreadSnapshotRecordMap2 = new HashMap<>();
        profileThreadSnapshotRecordMap2.put(ProfileThreadSnapshotRecord.TASK_ID, "task_id_2");
        profileThreadSnapshotRecordMap2.put(ProfileThreadSnapshotRecord.SEGMENT_ID, "segment_id_1");
        profileThreadSnapshotRecordMap2.put(ProfileThreadSnapshotRecord.DUMP_TIME, 2L);
        profileThreadSnapshotRecordMap2.put(ProfileThreadSnapshotRecord.SEQUENCE, 3);
        profileThreadSnapshotRecordMap2.put(ProfileThreadSnapshotRecord.STACK_BINARY, "U1RBQ0tfQklOQVJZ");
        profileThreadSnapshotRecordMap2.put(ProfileThreadSnapshotRecord.TIME_BUCKET, 4L);
        ProfileThreadSnapshotRecord profileThreadSnapshotRecord2 = profileThreadSnapshotRecordBuilder.storage2Entity(profileThreadSnapshotRecordMap2);

        Map<String, Object> profileThreadSnapshotRecordMap3 = new HashMap<>();
        profileThreadSnapshotRecordMap3.put(ProfileThreadSnapshotRecord.TASK_ID, "task_id_1");
        profileThreadSnapshotRecordMap3.put(ProfileThreadSnapshotRecord.SEGMENT_ID, "segment_id_1");
        profileThreadSnapshotRecordMap3.put(ProfileThreadSnapshotRecord.DUMP_TIME, 3L);
        profileThreadSnapshotRecordMap3.put(ProfileThreadSnapshotRecord.SEQUENCE, 0);
        profileThreadSnapshotRecordMap3.put(ProfileThreadSnapshotRecord.STACK_BINARY, "U1RBQ0tfQklOQVJZ");
        profileThreadSnapshotRecordMap3.put(ProfileThreadSnapshotRecord.TIME_BUCKET, 4L);
        ProfileThreadSnapshotRecord profileThreadSnapshotRecord3 = profileThreadSnapshotRecordBuilder.storage2Entity(profileThreadSnapshotRecordMap3);

        StorageHashMapBuilder<SegmentRecord> segmentRecordBuilder = new SegmentRecord.Builder();
        Map<String, Object> segmentRecordMap = new HashMap<>();
        segmentRecordMap.put(SegmentRecord.SEGMENT_ID, "segment_id_2");
        segmentRecordMap.put(SegmentRecord.TRACE_ID, "trace_id_2");
        segmentRecordMap.put(SegmentRecord.SERVICE_ID, "service_id_2");
        segmentRecordMap.put(SegmentRecord.SERVICE_INSTANCE_ID, "service_instance_id_2");
        segmentRecordMap.put(SegmentRecord.ENDPOINT_ID, "endpoint_id");
        segmentRecordMap.put(SegmentRecord.START_TIME, 1L);
        segmentRecordMap.put(SegmentRecord.LATENCY, 2);
        segmentRecordMap.put(SegmentRecord.IS_ERROR, 0);
        segmentRecordMap.put(SegmentRecord.DATA_BINARY, "REFUQV9CSU5BUlk=");
        segmentRecordMap.put(SegmentRecord.TIME_BUCKET, 3L);
        SegmentRecord segmentRecord1 = segmentRecordBuilder.storage2Entity(segmentRecordMap);
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag("tag1", "value1"));
        tags.add(new Tag("tag2", "value2"));
        segmentRecord1.setTags(Tag.Util.toStringList(tags));
        segmentRecord1.setTagsRawData(tags);

        segmentRecordMap.put(SegmentRecord.SEGMENT_ID, "segment_id_1");
        segmentRecordMap.put(SegmentRecord.START_TIME, 3L);
        SegmentRecord segmentRecord2 = segmentRecordBuilder.storage2Entity(segmentRecordMap);
        segmentRecord2.setTags(Tag.Util.toStringList(tags));
        segmentRecord2.setTagsRawData(tags);

        IoTDBInsertRequest request = new IoTDBInsertRequest(ProfileThreadSnapshotRecord.INDEX_NAME, 1L, profileThreadSnapshotRecord1, profileThreadSnapshotRecordBuilder);
        client.write(request);
        request = new IoTDBInsertRequest(ProfileThreadSnapshotRecord.INDEX_NAME, 2L, profileThreadSnapshotRecord2, profileThreadSnapshotRecordBuilder);
        client.write(request);
        request = new IoTDBInsertRequest(ProfileThreadSnapshotRecord.INDEX_NAME, 3L, profileThreadSnapshotRecord3, profileThreadSnapshotRecordBuilder);
        client.write(request);
        IRecordDAO recordDAO = new IoTDBStorageDAO(client).newRecordDao(segmentRecordBuilder);
        request = (IoTDBInsertRequest) recordDAO.prepareBatchInsert(segmentRecordModel, segmentRecord1);
        client.write(request);
        request = (IoTDBInsertRequest) recordDAO.prepareBatchInsert(segmentRecordModel, segmentRecord2);
        client.write(request);
    }

    @Test
    public void queryProfiledSegments() throws IOException {
        List<BasicTrace> basicTraceList = profileThreadSnapshotQueryDAO.queryProfiledSegments("task_id_1");
        long startTime = Long.MAX_VALUE;
        for (BasicTrace basicTrace : basicTraceList) {
            assert basicTrace.getSegmentId().equals("segment_id_1");
            long traceStartTime = Long.parseLong(basicTrace.getStart());
            assert traceStartTime <= startTime;
            startTime = traceStartTime;
        }
    }

    @Test
    public void queryMinSequence() throws IOException {
        int minValue = profileThreadSnapshotQueryDAO.queryMinSequence("segment_id_1", 0L, 10L);
        assert minValue == 0;
    }

    @Test
    public void queryMaxSequence() throws IOException {
        int maxValue = profileThreadSnapshotQueryDAO.queryMaxSequence("segment_id_1", 0L, 10L);
        assert maxValue == 3;
    }

    @Test
    public void queryRecords() throws IOException {
        List<ProfileThreadSnapshotRecord> records = profileThreadSnapshotQueryDAO.queryRecords("segment_id_1", 0, 10);
        records.forEach(record -> {
            assert record.getSegmentId().equals("segment_id_1");
        });
    }

    @Test
    public void getProfiledSegment() throws IOException {
        SegmentRecord segmentRecord = profileThreadSnapshotQueryDAO.getProfiledSegment("segment_id_2");
        assert segmentRecord.getSegmentId().equals("segment_id_2");
    }
}