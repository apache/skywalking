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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.Logs;
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

public class IoTDBLogQueryDAOTest {
    private IoTDBLogQueryDAO logQueryDAO;

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

        logQueryDAO = new IoTDBLogQueryDAO(client);

        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(LogRecord.class, LogRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model logRecordModel = new Model(
                LogRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(LogRecord.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(logRecordModel);

        StorageHashMapBuilder<LogRecord> logRecordBuilder = new LogRecord.Builder();
        Map<String, Object> logRecordMap = new HashMap<>();
        logRecordMap.put(LogRecord.UNIQUE_ID, "unique_id_1");
        logRecordMap.put(LogRecord.SERVICE_ID, "service_id_1");
        logRecordMap.put(LogRecord.SERVICE_INSTANCE_ID, "instance_id_1");
        logRecordMap.put(LogRecord.ENDPOINT_ID, "endpoint_id_1");
        logRecordMap.put(LogRecord.TRACE_ID, "trace_id_1");
        logRecordMap.put(LogRecord.TRACE_SEGMENT_ID, "trace_segment_id_1");
        logRecordMap.put(LogRecord.SPAN_ID, 1);
        logRecordMap.put(LogRecord.CONTENT_TYPE, ContentType.TEXT.value());
        logRecordMap.put(LogRecord.CONTENT, "content_1");
        logRecordMap.put(LogRecord.TIMESTAMP, 3L);
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag("tag1", "value1"));
        tags.add(new Tag("tag2", "value2"));
        Gson gson = new Gson();
        // TODO the TAGS_RAW_DATA value is not a viable test case
        logRecordMap.put(LogRecord.TAGS_RAW_DATA, new String(Base64.getEncoder().encode(gson.toJson(tags).getBytes(Charsets.UTF_8))));
        logRecordMap.put(LogRecord.TIME_BUCKET, 10000000000001L);
        LogRecord logRecord1 = logRecordBuilder.storage2Entity(logRecordMap);
        logRecord1.setTags(tags);
        logRecord1.setTagsInString(Tag.Util.toStringList(tags));

        logRecordMap.put(LogRecord.UNIQUE_ID, "unique_id_2");
        logRecordMap.put(LogRecord.TIMESTAMP, 4L);
        logRecordMap.put(LogRecord.TIME_BUCKET, 10000000000002L);
        LogRecord logRecord2 = logRecordBuilder.storage2Entity(logRecordMap);
        logRecord2.setTags(tags);
        logRecord2.setTagsInString(Tag.Util.toStringList(tags));

        IRecordDAO recordDAO = new IoTDBStorageDAO(client).newRecordDao(logRecordBuilder);
        IoTDBInsertRequest request = (IoTDBInsertRequest) recordDAO.prepareBatchInsert(logRecordModel, logRecord1);
        client.write(request);
        request = (IoTDBInsertRequest) recordDAO.prepareBatchInsert(logRecordModel, logRecord2);
        client.write(request);
    }

    @Test
    public void queryLogs() throws IOException {
        TraceScopeCondition relatedTrace = new TraceScopeCondition();
        relatedTrace.setTraceId("trace_id_1");
        relatedTrace.setSegmentId("trace_segment_id_1");
        relatedTrace.setSpanId(1);
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag("tag1", "value1"));
        tags.add(new Tag("tag2", "value2"));
        List<String> keywordsOfContent = new ArrayList<>();
        keywordsOfContent.add("1");
        List<String> excludingKeywordsOfContent = new ArrayList<>();
        excludingKeywordsOfContent.add("2");
        Logs logs = logQueryDAO.queryLogs("service_id_1", "instance_id_1", "endpoint_id_1",
                relatedTrace, Order.DES, 0, 10, 10000000000001L, 10000000000002L, tags,
                keywordsOfContent, excludingKeywordsOfContent);
        long timestamp = Long.MAX_VALUE;
        for (Log log : logs.getLogs()) {
            assert log.getServiceId().equals("service_id_1");
            assert log.getServiceInstanceId().equals("instance_id_1");
            assert log.getEndpointId().equals("endpoint_id_1");
            assert log.getTraceId().equals("trace_id_1");
            assert log.getTags().get(0).getKey().equals("tag1");
            assert log.getTags().get(0).getValue().equals("value1");
            assert log.getTags().get(1).getKey().equals("tag2");
            assert log.getTags().get(1).getValue().equals("value2");
            assert log.getContent().contains("1");
            assert !log.getContent().contains("2");
            assert log.getContentType().equals(ContentType.TEXT);
            assert log.getTimestamp() < timestamp;
            timestamp = log.getTimestamp();
        }
    }
}