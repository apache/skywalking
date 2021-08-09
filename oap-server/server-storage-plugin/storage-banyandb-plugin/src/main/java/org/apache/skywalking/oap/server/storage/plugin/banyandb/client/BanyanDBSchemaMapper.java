package org.apache.skywalking.oap.server.storage.plugin.banyandb.client;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import org.apache.skywalking.banyandb.Write;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BanyanDBSchemaMapper implements Function<SegmentRecord, Write.EntityValue> {
    private final SegmentRecord.Builder builder;

    private final Set<String> schemaKeys;
    /**
     * Map the key defined in BanyanDB schema (i.e. *.textproto) to the field name in the {@link SegmentRecord}
     */
    private final Map<String, String> keyMapping = new HashMap<>();

    /**
     * Map the key defined in BanyanDB schema to the Factory Lambda which can be invoked to build the Field
     */
    private final Map<String, Function<Map<String, Object>, Write.Field>> fieldsMap = new HashMap<>();

    public BanyanDBSchemaMapper(Set<String> schemaKeys) {
        this.builder = new SegmentRecord.Builder();
        this.schemaKeys = schemaKeys;

        // Known mapping
        // 1) duration -> latency
        this.keyMapping.put("duration", SegmentRecord.LATENCY);

        // iterate over keys in the schema
        for (final String key : schemaKeys) {
            fieldsMap.put(key, entityMap -> buildField(entityMap.get(keyMapping.getOrDefault(key, key))));
        }
    }

    @Override
    public Write.EntityValue apply(SegmentRecord segmentRecord) {
        final Map<String, Object> objectMap = this.builder.entity2Storage(segmentRecord);

        return Write.EntityValue.newBuilder()
                .addAllFields(this.schemaKeys.stream().map(s -> fieldsMap.get(s).apply(objectMap)).collect(Collectors.toList()))
                .setDataBinary(ByteString.copyFrom(segmentRecord.getDataBinary()))
                .setTimestamp(Timestamp.newBuilder().setSeconds(segmentRecord.getTimeBucket()).build())
                .setEntityId(segmentRecord.getSegmentId()).build();
    }

    static Write.Field buildField(Object value) {
        if (value instanceof String) {
            return Write.Field.newBuilder().setStr(Write.Str.newBuilder().setValue((String) value).build()).build();
        } else if (value instanceof Integer) {
            return Write.Field.newBuilder().setInt(Write.Int.newBuilder().setValue((Integer) value).build()).build();
        } else if (value instanceof Long) {
            return Write.Field.newBuilder().setInt(Write.Int.newBuilder().setValue((Long) value).build()).build();
        } else {
            throw new IllegalStateException("should not reach here");
        }
    }
}
