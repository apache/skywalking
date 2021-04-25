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

import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class H2AlarmRecordBuilder extends AbstractSearchTagBuilder<Record> {

    public H2AlarmRecordBuilder(final int maxSizeOfArrayColumn,
                                final int numOfSearchableValuesPerTag,
                                final List<String> searchTagKeys) {
        super(maxSizeOfArrayColumn, numOfSearchableValuesPerTag, searchTagKeys, AlarmRecord.TAGS);
    }

    @Override
    public Record storage2Entity(final Map<String, Object> dbMap) {
        AlarmRecord record = new AlarmRecord();
        record.setScope(((Number) dbMap.get(AlarmRecord.SCOPE)).intValue());
        record.setName((String) dbMap.get(AlarmRecord.NAME));
        record.setId0((String) dbMap.get(AlarmRecord.ID0));
        record.setId1((String) dbMap.get(AlarmRecord.ID1));
        record.setAlarmMessage((String) dbMap.get(AlarmRecord.ALARM_MESSAGE));
        record.setStartTime(((Number) dbMap.get(AlarmRecord.START_TIME)).longValue());
        record.setTimeBucket(((Number) dbMap.get(AlarmRecord.TIME_BUCKET)).longValue());
        record.setRuleName((String) dbMap.get(AlarmRecord.RULE_NAME));
        if (StringUtil.isEmpty((String) dbMap.get(AlarmRecord.TAGS_RAW_DATA))) {
            record.setTagsRawData(new byte[] {});
        } else {
            // Don't read the tags as they has been in the data binary already.
            record.setTagsRawData(Base64.getDecoder().decode((String) dbMap.get(AlarmRecord.TAGS_RAW_DATA)));
        }
        return record;
    }

    @Override
    public Map<String, Object> entity2Storage(final Record record) {
        AlarmRecord storageData = (AlarmRecord) record;
        Map<String, Object> map = new HashMap<>();
        map.put(AlarmRecord.SCOPE, storageData.getScope());
        map.put(AlarmRecord.NAME, storageData.getName());
        map.put(AlarmRecord.ID0, storageData.getId0());
        map.put(AlarmRecord.ID1, storageData.getId1());
        map.put(AlarmRecord.ALARM_MESSAGE, storageData.getAlarmMessage());
        map.put(AlarmRecord.START_TIME, storageData.getStartTime());
        map.put(AlarmRecord.TIME_BUCKET, storageData.getTimeBucket());
        map.put(AlarmRecord.RULE_NAME, storageData.getRuleName());
        if (CollectionUtils.isEmpty(storageData.getTagsRawData())) {
            map.put(AlarmRecord.TAGS_RAW_DATA, Const.EMPTY_STRING);
        } else {
            map.put(AlarmRecord.TAGS_RAW_DATA, new String(Base64.getEncoder().encode(storageData.getTagsRawData())));
        }
        analysisSearchTag(storageData.getTags(), map);
        return map;
    }
}
