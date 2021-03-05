/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.TAGS_RAW_DATA;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord.CONTENT;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord.CONTENT_TYPE;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord.ENDPOINT_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord.ENDPOINT_NAME;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord.SERVICE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord.SERVICE_INSTANCE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord.SPAN_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord.TIMESTAMP;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord.TRACE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord.TRACE_SEGMENT_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord.UNIQUE_ID;
import static org.apache.skywalking.oap.server.core.analysis.record.Record.TIME_BUCKET;

public class H2LogRecordBuilder extends AbstractSearchTagBuilder<Record> {

    public H2LogRecordBuilder(final int maxSizeOfArrayColumn,
                              final int numOfSearchableValuesPerTag,
                              final List<String> searchTagKeys) {
        super(maxSizeOfArrayColumn, numOfSearchableValuesPerTag, searchTagKeys, LogRecord.TAGS);
    }

    @Override
    public Record storage2Entity(final Map<String, Object> dbMap) {
        LogRecord record = new LogRecord();
        record.setUniqueId((String) dbMap.get(UNIQUE_ID));
        record.setServiceId((String) dbMap.get(SERVICE_ID));
        record.setServiceInstanceId((String) dbMap.get(SERVICE_INSTANCE_ID));
        record.setEndpointId((String) dbMap.get(ENDPOINT_ID));
        record.setEndpointName((String) dbMap.get(ENDPOINT_NAME));
        record.setTraceId((String) dbMap.get(TRACE_ID));
        record.setTraceSegmentId((String) dbMap.get(TRACE_SEGMENT_ID));
        record.setSpanId(((Number) dbMap.get(SPAN_ID)).intValue());
        record.setContentType(((Number) dbMap.get(CONTENT_TYPE)).intValue());
        record.setContent((String) dbMap.get(CONTENT));
        record.setTimestamp(((Number) dbMap.get(TIMESTAMP)).longValue());
        record.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
        if (StringUtil.isEmpty((String) dbMap.get(TAGS_RAW_DATA))) {
            record.setTagsRawData(new byte[] {});
        } else {
            // Don't read the tags as they has been in the data binary already.
            record.setTagsRawData(Base64.getDecoder().decode((String) dbMap.get(TAGS_RAW_DATA)));
        }
        return record;
    }

    @Override
    public Map<String, Object> entity2Storage(final Record record) {
        LogRecord storageData = (LogRecord) record;
        Map<String, Object> map = new HashMap<>();
        map.put(UNIQUE_ID, storageData.getUniqueId());
        map.put(SERVICE_ID, storageData.getServiceId());
        map.put(SERVICE_INSTANCE_ID, storageData.getServiceInstanceId());
        map.put(ENDPOINT_ID, storageData.getEndpointId());
        map.put(ENDPOINT_NAME, storageData.getEndpointName());
        map.put(TRACE_ID, storageData.getTraceId());
        map.put(TRACE_SEGMENT_ID, storageData.getTraceSegmentId());
        map.put(SPAN_ID, storageData.getSpanId());
        map.put(TIME_BUCKET, storageData.getTimeBucket());
        map.put(CONTENT_TYPE, storageData.getContentType());
        map.put(CONTENT, storageData.getContent());
        map.put(TIMESTAMP, storageData.getTimestamp());
        if (CollectionUtils.isEmpty(storageData.getTagsRawData())) {
            map.put(TAGS_RAW_DATA, Const.EMPTY_STRING);
        } else {
            map.put(TAGS_RAW_DATA, new String(Base64.getEncoder().encode(storageData.getTagsRawData())));
        }
        analysisSearchTag(storageData.getTags(), map);
        return map;
    }
}
