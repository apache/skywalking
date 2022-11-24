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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import static org.apache.skywalking.oap.server.core.analysis.record.Record.TIME_BUCKET;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ALARM;

@Getter
@Setter
@ScopeDeclaration(id = ALARM, name = "Alarm")
@Stream(name = AlarmRecord.INDEX_NAME, scopeId = DefaultScopeDefine.ALARM, builder = AlarmRecord.Builder.class, processor = RecordStreamProcessor.class)
@SQLDatabase.ExtraColumn4AdditionalEntity(additionalTable = AlarmRecord.ADDITIONAL_TAG_TABLE, parentColumn = TIME_BUCKET)
@BanyanDB.TimestampColumn(AlarmRecord.START_TIME)
public class AlarmRecord extends Record {

    public static final String INDEX_NAME = "alarm_record";
    public static final String ADDITIONAL_TAG_TABLE = "alarm_record_tag";
    public static final String SCOPE = "scope";
    public static final String NAME = "name";
    public static final String ID0 = "id0";
    public static final String ID1 = "id1";
    public static final String START_TIME = "start_time";
    public static final String ALARM_MESSAGE = "alarm_message";
    public static final String RULE_NAME = "rule_name";
    public static final String TAGS = "tags";
    public static final String TAGS_RAW_DATA = "tags_raw_data";

    @Override
    public String id() {
        return getTimeBucket() + Const.ID_CONNECTOR + ruleName + Const.ID_CONNECTOR + id0 + Const.ID_CONNECTOR + id1;
    }

    @Column(columnName = SCOPE)
    private int scope;
    @Column(columnName = NAME, storageOnly = true, length = 512)
    private String name;
    @Column(columnName = ID0, storageOnly = true, length = 512)
    @BanyanDB.ShardingKey(index = 0)
    private String id0;
    @Column(columnName = ID1, storageOnly = true)
    private String id1;
    @Column(columnName = START_TIME)
    private long startTime;
    @Column(columnName = ALARM_MESSAGE)
    @ElasticSearch.MatchQuery
    private String alarmMessage;
    @Column(columnName = RULE_NAME)
    private String ruleName;
    @Column(columnName = TAGS, indexOnly = true)
    @SQLDatabase.AdditionalEntity(additionalTables = {ADDITIONAL_TAG_TABLE})
    private List<String> tagsInString;
    @Column(columnName = TAGS_RAW_DATA, storageOnly = true, length = Tag.TAG_LENGTH)
    private byte[] tagsRawData;

    public static class Builder implements StorageBuilder<AlarmRecord> {
        @Override
        public AlarmRecord storage2Entity(final Convert2Entity converter) {
            AlarmRecord record = new AlarmRecord();
            record.setScope(((Number) converter.get(SCOPE)).intValue());
            record.setName((String) converter.get(NAME));
            record.setId0((String) converter.get(ID0));
            record.setId1((String) converter.get(ID1));
            record.setAlarmMessage((String) converter.get(ALARM_MESSAGE));
            record.setStartTime(((Number) converter.get(START_TIME)).longValue());
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            record.setRuleName((String) converter.get(RULE_NAME));
            record.setTagsRawData(converter.getBytes(TAGS_RAW_DATA));
            // Don't read the TAGS as they are only for query.
            return record;
        }

        @Override
        public void entity2Storage(final AlarmRecord storageData, final Convert2Storage converter) {
            converter.accept(SCOPE, storageData.getScope());
            converter.accept(NAME, storageData.getName());
            converter.accept(ID0, storageData.getId0());
            converter.accept(ID1, storageData.getId1());
            converter.accept(ALARM_MESSAGE, storageData.getAlarmMessage());
            converter.accept(START_TIME, storageData.getStartTime());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(RULE_NAME, storageData.getRuleName());
            converter.accept(TAGS_RAW_DATA, storageData.getTagsRawData());
            converter.accept(TAGS, storageData.getTagsInString());
        }
    }
}
