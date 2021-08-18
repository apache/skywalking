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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.banyandb.Write;
import org.apache.skywalking.banyandb.client.request.WriteValue;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BanyanDBSegmentRecordBuilderTest {
    private StorageHashMapBuilder<Record> builder;

    @Before
    public void setUp() {
        this.builder = new BanyanDBSegmentRecordBuilder();
    }

    @Test
    public void testBuilder() {
        String segmentId = "1231.dfd.123123ssf";
        String traceId = "trace_id-xxfff.111323";
        String serviceId = "webapp_id";
        String serviceInstanceId = "10.0.0.1_id";
        String endpointId = "home_id";
        int latency = 200;
        int state = 1;
        Instant now = Instant.now();
        byte[] byteData = new byte[]{14};
        List<Tag> tags = new ArrayList<>();
        String broker = "172.16.10.129:9092";
        String topic = "topic_1";
        String queue = "queue_2";
        String httpMethod = "GET";
        String httpStatusCode = "200";
        String dbType = "SQL";
        String dbInstance = "127.0.0.1:3306";
        tags.add(new Tag("mq.broker", broker));
        tags.add(new Tag("mq.topic", topic));
        tags.add(new Tag("mq.queue", queue));
        tags.add(new Tag("http.method", httpMethod));
        tags.add(new Tag("status_code", httpStatusCode));
        tags.add(new Tag("db.type", dbType));
        tags.add(new Tag("db.instance", dbInstance));
        final SegmentRecord record = new SegmentRecord();
        record.setSegmentId(segmentId);
        record.setTraceId(traceId);
        record.setServiceId(serviceId);
        record.setServiceInstanceId(serviceInstanceId);
        record.setEndpointId(endpointId);
        record.setStartTime(now.getEpochSecond());
        record.setLatency(latency);
        record.setIsError(state);
        record.setTimeBucket(now.getEpochSecond());
        record.setDataBinary(byteData);
        record.setTagsRawData(tags);
        List<Write.Field> value = BanyanDBRecordDAO.buildFieldObjects(builder.entity2Storage(record)).stream().map(WriteValue::toWriteField).collect(Collectors.toList());
        // 0 -> trace_id
        Assert.assertEquals(traceId, value.get(0).getStr().getValue());
        // 1 -> state
        Assert.assertEquals(state, value.get(1).getInt().getValue());
        // 2 -> service_id
        Assert.assertEquals(serviceId, value.get(2).getStr().getValue());
        // 3 -> service_instance_id
        Assert.assertEquals(serviceInstanceId, value.get(3).getStr().getValue());
        // 4 -> endpoint_id
        Assert.assertEquals(endpointId, value.get(4).getStr().getValue());
        // 5 -> duration
        Assert.assertEquals(latency, value.get(5).getInt().getValue());
        // 6 -> start_time
        Assert.assertEquals(now.getEpochSecond(), value.get(6).getInt().getValue());
        // 7 -> http.method
        Assert.assertEquals(httpMethod, value.get(7).getStr().getValue());
        // 8 -> status_code
        Assert.assertEquals(httpStatusCode, value.get(8).getStr().getValue());
        // 9 -> db.type
        Assert.assertEquals(dbType, value.get(9).getStr().getValue());
        // 10 -> db.instance
        Assert.assertEquals(dbInstance, value.get(10).getStr().getValue());
        // 11 -> mq.queue
        Assert.assertEquals(queue, value.get(11).getStr().getValue());
        // 12 -> mq.topic
        Assert.assertEquals(topic, value.get(12).getStr().getValue());
        // 13 -> mq.broker
        Assert.assertEquals(broker, value.get(13).getStr().getValue());
    }
}
