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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;

import java.util.HashMap;
import java.util.Map;

public class BanyanDBSegmentRecordBuilder implements StorageHashMapBuilder<Record> {
    @Override
    public SegmentRecord storage2Entity(Map<String, Object> dbMap) {
        return new SegmentRecord();
    }

    /**
     * Map SegmentRecord to Skywalking-BanyanDB compatible Map with indexed tags and
     * without binaryData, entityId
     */
    @Override
    public Map<String, Object> entity2Storage(Record record) {
        final SegmentRecord segmentRecord = (SegmentRecord) record;
        Map<String, Object> map = new HashMap<>();
        map.put(SegmentRecord.TRACE_ID, TagAndValue.stringField(segmentRecord.getTraceId()));
        map.put(SegmentRecord.SERVICE_ID, TagAndValue.stringField(segmentRecord.getServiceId()));
        map.put(SegmentRecord.SERVICE_INSTANCE_ID, TagAndValue.stringField(segmentRecord.getServiceInstanceId()));
        map.put(SegmentRecord.ENDPOINT_ID, TagAndValue.stringField(segmentRecord.getEndpointId()));
        map.put(SegmentRecord.START_TIME, TagAndValue.longField(segmentRecord.getStartTime()));
        map.put("duration", TagAndValue.longField(segmentRecord.getLatency()));
        map.put("state", TagAndValue.longField(segmentRecord.getIsError()));
        if (segmentRecord.getTagsRawData() != null) {
            for (final Tag tag : segmentRecord.getTagsRawData()) {
                map.put(tag.getKey().toLowerCase(), TagAndValue.stringField(tag.getValue()));
            }
        }
        return map;
    }
}
