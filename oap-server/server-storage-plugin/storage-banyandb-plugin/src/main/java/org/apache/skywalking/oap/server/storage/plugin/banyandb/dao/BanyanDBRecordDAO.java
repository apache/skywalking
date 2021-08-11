package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBSchema;
import org.apache.skywalking.banyandb.client.request.TraceWriteRequest;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class BanyanDBRecordDAO implements IRecordDAO {
    private final StorageHashMapBuilder<Record> storageBuilder;

    @Override
    public InsertRequest prepareBatchInsert(Model model, Record record) throws IOException {
        if (SegmentRecord.INDEX_NAME.equals(model.getName())) {
            SegmentRecord segmentRecord = (SegmentRecord) record;
            TraceWriteRequest request = TraceWriteRequest.builder()
                    .dataBinary(segmentRecord.getDataBinary())
                    .timestampSeconds(record.getTimeBucket())
                    .entityId(segmentRecord.getSegmentId())
                    .fields(buildFieldObjects(this.storageBuilder.entity2Storage(segmentRecord)))
                    .build();
            return new BanyanDBTraceInsertRequest(request);
        }
        return new InsertRequest() {
        };
    }

    /**
     * Convert storageEntity in Map to a ordered list of Objects
     *
     * @param segmentRecordMap which comes from {@link SegmentRecord}
     * @return a ordered list of {@link Object}s which is accepted by BanyanDB Client
     */
    static List<Object> buildFieldObjects(Map<String, Object> segmentRecordMap) {
        List<Object> objectList = new ArrayList<>(BanyanDBSchema.FIELD_NAMES.size());
        for (String fieldName : BanyanDBSchema.FIELD_NAMES) {
            objectList.add(segmentRecordMap.get(fieldName));
        }
        return objectList;
    }
}
