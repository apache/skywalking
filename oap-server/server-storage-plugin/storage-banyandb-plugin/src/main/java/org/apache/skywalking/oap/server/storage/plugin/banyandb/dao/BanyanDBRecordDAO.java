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

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.client.request.WriteValue;
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
                    .timestampSeconds(segmentRecord.getStartTime())
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
     * @return an ordered list of {@link Object}s which is accepted by BanyanDB Client
     */
    static List<WriteValue<?>> buildFieldObjects(Map<String, Object> segmentRecordMap) {
        List<WriteValue<?>> objectList = new ArrayList<>(BanyanDBSchema.FIELD_NAMES.size());
        for (String fieldName : BanyanDBSchema.FIELD_NAMES) {
            Object val = segmentRecordMap.get(fieldName);
            if (val == null) {
                objectList.add(WriteValue.nullValue());
            } else {
                objectList.add((WriteValue<?>) val);
            }
        }
        return objectList;
    }
}
