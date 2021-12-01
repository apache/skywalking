package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;

import java.util.Collections;
import java.util.List;

public class SegmentRecordMapper implements RowEntityMapper<SegmentRecord> {
    @Override
    public SegmentRecord map(RowEntity row) {
        SegmentRecord record = new SegmentRecord();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        record.setSegmentId(row.getId());
        record.setTraceId((String) searchable.get(0).getValue());
        record.setIsError(((Number) searchable.get(1).getValue()).intValue());
        record.setServiceId((String) searchable.get(2).getValue());
        record.setServiceInstanceId((String) searchable.get(3).getValue());
        record.setEndpointId((String) searchable.get(4).getValue());
        record.setLatency(((Number) searchable.get(5).getValue()).intValue());
        record.setStartTime(((Number) searchable.get(6).getValue()).longValue());
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        record.setDataBinary(((ByteString) data.get(0).getValue()).toByteArray());
        return record;
    }

    @Override
    public List<String> searchableProjection() {
        return ImmutableList.of("trace_id", "state", "service_id", "service_instance_id", "endpoint_id", "duration", "start_time");
    }

    @Override
    public List<String> dataProjection() {
        return Collections.singletonList("data_binary");
    }
}
