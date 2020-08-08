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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import joptsimple.internal.Strings;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.DATA_BINARY;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.ENDPOINT_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.ENDPOINT_NAME;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.END_TIME;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.IS_ERROR;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.LATENCY;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.SEGMENT_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.SERVICE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.SERVICE_INSTANCE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.START_TIME;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.TAGS;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.TIME_BUCKET;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.TRACE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.VERSION;

/**
 * H2/MySQL is different from standard {@link org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.Builder},
 * this maps the tags into multiple columns.
 */
public class H2SegmentRecordBuilder implements StorageBuilder<Record> {
    private int numOfSearchableValuesPerTag;
    private final List<String> searchTagKeys;

    public H2SegmentRecordBuilder(final int maxSizeOfArrayColumn,
                                  final int numOfSearchableValuesPerTag,
                                  final List<String> searchTagKeys) {
        this.numOfSearchableValuesPerTag = numOfSearchableValuesPerTag;
        final int maxNumOfTags = maxSizeOfArrayColumn / numOfSearchableValuesPerTag;
        if (searchTagKeys.size() > maxNumOfTags) {
            this.searchTagKeys = searchTagKeys.subList(0, maxNumOfTags);
        } else {
            this.searchTagKeys = searchTagKeys;
        }
    }

    @Override
    public Map<String, Object> data2Map(Record record) {
        SegmentRecord storageData = (SegmentRecord) record;
        storageData.setStatement(Strings.join(new String[] {
            storageData.getEndpointName(),
            storageData.getTraceId()
        }, " - "));
        Map<String, Object> map = new HashMap<>();
        map.put(SEGMENT_ID, storageData.getSegmentId());
        map.put(TRACE_ID, storageData.getTraceId());
        map.put(TopN.STATEMENT, storageData.getStatement());
        map.put(SERVICE_ID, storageData.getServiceId());
        map.put(SERVICE_INSTANCE_ID, storageData.getServiceInstanceId());
        map.put(ENDPOINT_NAME, storageData.getEndpointName());
        map.put(ENDPOINT_ID, storageData.getEndpointId());
        map.put(START_TIME, storageData.getStartTime());
        map.put(END_TIME, storageData.getEndTime());
        map.put(LATENCY, storageData.getLatency());
        map.put(IS_ERROR, storageData.getIsError());
        map.put(TIME_BUCKET, storageData.getTimeBucket());
        if (CollectionUtils.isEmpty(storageData.getDataBinary())) {
            map.put(DATA_BINARY, Const.EMPTY_STRING);
        } else {
            map.put(DATA_BINARY, new String(Base64.getEncoder().encode(storageData.getDataBinary())));
        }
        map.put(VERSION, storageData.getVersion());
        storageData.getTagsRawData().forEach(spanTag -> {
            final int index = searchTagKeys.indexOf(spanTag.getKey());
            boolean shouldAdd = true;
            int tagIdx = 0;
            final String tagExpression = spanTag.toString();
            for (int i = 0; i < numOfSearchableValuesPerTag; i++) {
                tagIdx = index * numOfSearchableValuesPerTag + i;
                final String previousValue = (String) map.get(TAGS + "_" + tagIdx);
                if (previousValue == null) {
                    // Still have at least one available slot, add directly.
                    shouldAdd = true;
                    break;
                }
                // If value is duplicated with added one, ignore.
                if (previousValue.equals(tagExpression)) {
                    shouldAdd = false;
                    break;
                }
                // Reach the end of tag
                if (i == numOfSearchableValuesPerTag - 1) {
                    shouldAdd = false;
                }
            }
            if (shouldAdd) {
                map.put(TAGS + "_" + tagIdx, tagExpression);
            }
        });
        return map;
    }

    @Override
    public Record map2Data(Map<String, Object> dbMap) {
        SegmentRecord record = new SegmentRecord();
        record.setSegmentId((String) dbMap.get(SEGMENT_ID));
        record.setTraceId((String) dbMap.get(TRACE_ID));
        record.setStatement((String) dbMap.get(TopN.STATEMENT));
        record.setServiceId((String) dbMap.get(SERVICE_ID));
        record.setServiceInstanceId((String) dbMap.get(SERVICE_INSTANCE_ID));
        record.setEndpointName((String) dbMap.get(ENDPOINT_NAME));
        record.setEndpointId((String) dbMap.get(ENDPOINT_ID));
        record.setStartTime(((Number) dbMap.get(START_TIME)).longValue());
        record.setEndTime(((Number) dbMap.get(END_TIME)).longValue());
        record.setLatency(((Number) dbMap.get(LATENCY)).intValue());
        record.setIsError(((Number) dbMap.get(IS_ERROR)).intValue());
        record.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
        if (StringUtil.isEmpty((String) dbMap.get(DATA_BINARY))) {
            record.setDataBinary(new byte[] {});
        } else {
            record.setDataBinary(Base64.getDecoder().decode((String) dbMap.get(DATA_BINARY)));
        }
        record.setVersion(((Number) dbMap.get(VERSION)).intValue());
        // Don't read the tags as they has been in the data binary already.
        return record;
    }
}