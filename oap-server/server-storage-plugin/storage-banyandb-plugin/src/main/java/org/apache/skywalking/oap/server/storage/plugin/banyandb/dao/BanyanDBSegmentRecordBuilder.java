package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;

import java.util.HashMap;
import java.util.Map;

public class BanyanDBSegmentRecordBuilder implements StorageHashMapBuilder<Record> {
    @Override
    public SegmentRecord storage2Entity(Map<String, Object> dbMap) {
        return null;
    }

    /**
     * Map SegmentRecord to Skywalking-BanyanDB compatible Map with indexed tags and
     * without binaryData, entityId
     *
     * @param record
     * @return
     */
    @Override
    public Map<String, Object> entity2Storage(Record record) {
        final SegmentRecord segmentRecord = (SegmentRecord) record;
        Map<String, Object> map = new HashMap<>();
        map.put(SegmentRecord.TRACE_ID, segmentRecord.getTraceId());
        map.put(SegmentRecord.SERVICE_ID, segmentRecord.getServiceId());
        map.put(SegmentRecord.SERVICE_INSTANCE_ID, segmentRecord.getServiceInstanceId());
        map.put(SegmentRecord.ENDPOINT_ID, segmentRecord.getEndpointId());
        map.put(SegmentRecord.START_TIME, segmentRecord.getStartTime());
        map.put("duration", segmentRecord.getLatency());
        map.put("state", segmentRecord.getIsError());
        if (segmentRecord.getTagsRawData() != null) {
            for (final Tag tag : segmentRecord.getTagsRawData()) {
                map.put(tag.getKey().toLowerCase(), tag.getValue());
            }
        }
        return map;
    }
}
