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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.client;

import com.google.protobuf.NullValue;
import org.apache.skywalking.banyandb.Write;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBSchema;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BanyanDBSchemaMapperTest {
    private BanyanDBSchema schema;
    private BanyanDBSchemaMapper mapper;

    @Before
    public void setup() {
        this.schema = BanyanDBSchema.fromTextProtoResource("trace_series.textproto");
        this.mapper = new BanyanDBSchemaMapper(this.schema.getFieldNames());
    }

    @Test
    public void testNonNull() {
        Assert.assertNotNull(mapper);
    }

    @Test
    public void testSegmentRecordConversion() {
        String segmentId = "1231.dfd.123123ssf";
        String traceId = "trace_id-xxfff.111323";
        String serviceId = "webapp_id";
        String serviceInstanceId = "10.0.0.1_id";
        String endpointName = "/home";
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
        record.setEndpointName(endpointName);
        record.setEndpointId(endpointId);
        record.setStartTime(now.getEpochSecond());
        record.setLatency(latency);
        record.setIsError(state);
        record.setTimeBucket(now.getEpochSecond());
        record.setDataBinary(byteData);
        record.setTagsRawData(tags);
        Write.EntityValue value = mapper.apply(record);
        // the length of fields in the EntityValue MUST be equal to the schema length
        Assert.assertEquals(this.schema.getFieldNames().size(), value.getFieldsCount());
        Assert.assertArrayEquals(byteData, value.getDataBinary().toByteArray());
        Assert.assertEquals(segmentId, value.getEntityId());
        // 0 -> trace_id
        Assert.assertEquals(traceId, value.getFields(0).getStr().getValue());
        // 1 -> state
        Assert.assertEquals(state, value.getFields(1).getInt().getValue());
        // 2 -> service_id
        Assert.assertEquals(serviceId, value.getFields(2).getStr().getValue());
        // 3 -> service_instance_id
        Assert.assertEquals(serviceInstanceId, value.getFields(3).getStr().getValue());
        // 4 -> endpoint_id
        Assert.assertEquals(endpointId, value.getFields(4).getStr().getValue());
        // TODO: 5 -> service_name
        Assert.assertEquals(NullValue.NULL_VALUE, value.getFields(5).getNull());
        // TODO: 6 -> service_instance_name
        Assert.assertEquals(NullValue.NULL_VALUE, value.getFields(6).getNull());
        // 7 -> endpoint_name
        Assert.assertEquals(endpointName, value.getFields(7).getStr().getValue());
        // 8 -> duration
        Assert.assertEquals(latency, value.getFields(8).getInt().getValue());
        // 9 -> start_time
        Assert.assertEquals(now.getEpochSecond(), value.getFields(9).getInt().getValue());
        // 10 -> http.method
        Assert.assertEquals(httpMethod, value.getFields(10).getStr().getValue());
        // 11 -> status_code
        Assert.assertEquals(httpStatusCode, value.getFields(11).getStr().getValue());
        // 12 -> db.type
        Assert.assertEquals(dbType, value.getFields(12).getStr().getValue());
        // 13 -> db.instance
        Assert.assertEquals(dbInstance, value.getFields(13).getStr().getValue());
        // 14 -> mq.queue
        Assert.assertEquals(queue, value.getFields(14).getStr().getValue());
        // 15 -> mq.topic
        Assert.assertEquals(topic, value.getFields(15).getStr().getValue());
        // 16 -> mq.broker
        Assert.assertEquals(broker, value.getFields(16).getStr().getValue());
    }
}
