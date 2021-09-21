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
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.ExtraQueryIndex;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
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

public class IoTDBTraceQueryDAOTest {
    private IoTDBTraceQueryDAO traceQueryDAO;

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

        traceQueryDAO = new IoTDBTraceQueryDAO(client);

        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(SegmentRecord.class, SegmentRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model segmentRecordModel = new Model(
                SegmentRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(SegmentRecord.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(segmentRecordModel);

        StorageHashMapBuilder<SegmentRecord> segmentRecordBuilder = new SegmentRecord.Builder();
        Map<String, Object> segmentRecordMap = new HashMap<>();
        segmentRecordMap.put(SegmentRecord.SEGMENT_ID, "segment_id_3");
        segmentRecordMap.put(SegmentRecord.TRACE_ID, "trace_id_1");
        segmentRecordMap.put(SegmentRecord.SERVICE_ID, "service_id_1");
        segmentRecordMap.put(SegmentRecord.SERVICE_INSTANCE_ID, "service_instance_id_1");
        segmentRecordMap.put(SegmentRecord.ENDPOINT_ID, "endpoint_id");
        segmentRecordMap.put(SegmentRecord.START_TIME, 2L);
        segmentRecordMap.put(SegmentRecord.LATENCY, 3);
        segmentRecordMap.put(SegmentRecord.IS_ERROR, BooleanUtils.FALSE);
        segmentRecordMap.put(SegmentRecord.DATA_BINARY, "REFUQV9CSU5BUlk=");
        segmentRecordMap.put(SegmentRecord.TIME_BUCKET, 10000000000001L);
        SegmentRecord segmentRecord1 = segmentRecordBuilder.storage2Entity(segmentRecordMap);
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag("tag3", "value3"));
        segmentRecord1.setTags(Tag.Util.toStringList(tags));
        segmentRecord1.setTagsRawData(tags);

        IRecordDAO recordDAO = new IoTDBStorageDAO(client).newRecordDao(segmentRecordBuilder);
        IoTDBInsertRequest request = (IoTDBInsertRequest) recordDAO.prepareBatchInsert(segmentRecordModel, segmentRecord1);
        client.write(request);

        segmentRecordMap.put(SegmentRecord.SEGMENT_ID, "segment_id_4");
        segmentRecordMap.put(SegmentRecord.START_TIME, 5L);
        segmentRecordMap.put(SegmentRecord.LATENCY, 4);
        segmentRecordMap.put(SegmentRecord.TIME_BUCKET, 10000000000002L);
        SegmentRecord segmentRecord2 = segmentRecordBuilder.storage2Entity(segmentRecordMap);
        segmentRecord2.setTags(Tag.Util.toStringList(tags));
        segmentRecord2.setTagsRawData(tags);

        request = (IoTDBInsertRequest) recordDAO.prepareBatchInsert(segmentRecordModel, segmentRecord2);
        client.write(request);
    }

    @Test
    public void queryBasicTraces() throws IOException {
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag("tag3", "value3"));
        TraceBrief traceBrief = traceQueryDAO.queryBasicTraces(10000000000001L, 10000000000002L,
                1L, 10L, "", "service_instance_id_1",
                "endpoint_id", "", 10, 0, TraceState.SUCCESS,
                QueryOrder.BY_START_TIME, tags);
        long startTime = Long.MAX_VALUE;
        for (BasicTrace trace : traceBrief.getTraces()) {
            assertThat(Long.parseLong(trace.getStart())).isLessThanOrEqualTo(startTime);
            startTime = Long.parseLong(trace.getStart());
            assertThat(trace.getDuration()).isGreaterThanOrEqualTo(1);
            assertThat(trace.getDuration()).isLessThanOrEqualTo(10);
            assertThat(trace.isError()).isFalse();
            for (String traceId : trace.getTraceIds()) {
                assertThat(traceId).isEqualTo("trace_id_1");
            }
        }
    }

    @Test
    public void queryByTraceId() throws IOException {
        List<SegmentRecord> segmentRecordList = traceQueryDAO.queryByTraceId("trace_id_1");
        segmentRecordList.forEach(segmentRecord -> {
            assertThat(segmentRecord.getTraceId()).isEqualTo("trace_id_1");
        });
    }
}