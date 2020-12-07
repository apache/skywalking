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

package org.apache.skywalking.oap.server.core.alarm;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

import java.util.HashMap;
import java.util.Map;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ALARM;

@Getter
@Setter
@ScopeDeclaration(id = ALARM, name = "Alarm")
@Stream(name = AlarmRecord.INDEX_NAME, scopeId = DefaultScopeDefine.ALARM, builder = AlarmRecord.Builder.class, processor = RecordStreamProcessor.class)
public class AlarmRecord extends Record {

    public static final String INDEX_NAME = "alarm_record";
    public static final String SCOPE = "scope";
    public static final String NAME = "name";
    public static final String ID0 = "id0";
    public static final String ID1 = "id1";
    public static final String START_TIME = "start_time";
    public static final String ALARM_MESSAGE = "alarm_message";
    public static final String RULE_NAME = "rule_name";

    @Override
    public String id() {
        return getTimeBucket() + Const.ID_CONNECTOR + ruleName + Const.ID_CONNECTOR + id0 + Const.ID_CONNECTOR + id1;
    }

    @Column(columnName = SCOPE)
    private int scope;
    @Column(columnName = NAME, storageOnly = true)
    private String name;
    @Column(columnName = ID0, storageOnly = true)
    private String id0;
    @Column(columnName = ID1, storageOnly = true)
    private String id1;
    @Column(columnName = START_TIME)
    private long startTime;
    @Column(columnName = ALARM_MESSAGE, matchQuery = true)
    private String alarmMessage;
    @Column(columnName = RULE_NAME)
    private String ruleName;

    public static class Builder implements StorageBuilder<AlarmRecord> {

        @Override
        public Map<String, Object> data2Map(AlarmRecord storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SCOPE, storageData.getScope());
            map.put(NAME, storageData.getName());
            map.put(ID0, storageData.getId0());
            map.put(ID1, storageData.getId1());
            map.put(ALARM_MESSAGE, storageData.getAlarmMessage());
            map.put(START_TIME, storageData.getStartTime());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            map.put(RULE_NAME, storageData.getRuleName());
            return map;
        }

        @Override
        public AlarmRecord map2Data(Map<String, Object> dbMap) {
            AlarmRecord record = new AlarmRecord();
            record.setScope(((Number) dbMap.get(SCOPE)).intValue());
            record.setName((String) dbMap.get(NAME));
            record.setId0((String) dbMap.get(ID0));
            record.setId1((String) dbMap.get(ID1));
            record.setAlarmMessage((String) dbMap.get(ALARM_MESSAGE));
            record.setStartTime(((Number) dbMap.get(START_TIME)).longValue());
            record.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            record.setRuleName((String) dbMap.get(RULE_NAME));
            return record;
        }
    }
}
