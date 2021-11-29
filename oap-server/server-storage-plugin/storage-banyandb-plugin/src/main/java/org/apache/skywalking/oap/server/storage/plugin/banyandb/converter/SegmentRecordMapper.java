package org.apache.skywalking.oap.server.storage.plugin.banyandb.converter;

import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;

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
        // TODO: support binary data in the client SDK
        record.setDataBinary((byte[]) data.get(0).getValue());
        return record;
    }
}
