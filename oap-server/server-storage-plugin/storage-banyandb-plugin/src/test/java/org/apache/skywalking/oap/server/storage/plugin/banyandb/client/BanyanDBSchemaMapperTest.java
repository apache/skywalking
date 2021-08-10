package org.apache.skywalking.oap.server.storage.plugin.banyandb.client;

import com.google.protobuf.NullValue;
import org.apache.skywalking.banyandb.Write;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBSchema;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;

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
        // TODO: other tags
    }
}
