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

package org.apache.skywalking.oap.server.core.profiling.trace;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROFILE_TASK_SEGMENT_SNAPSHOT;

/**
 * Profiling segment snapshot database bean, use record
 */
@Getter
@Setter
@ScopeDeclaration(id = PROFILE_TASK_SEGMENT_SNAPSHOT, name = "ProfileThreadSnapshot")
@Stream(name = ProfileThreadSnapshotRecord.INDEX_NAME, scopeId = PROFILE_TASK_SEGMENT_SNAPSHOT, builder = ProfileThreadSnapshotRecord.Builder.class, processor = RecordStreamProcessor.class)
@BanyanDB.TimestampColumn(ProfileThreadSnapshotRecord.DUMP_TIME)
@BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS)
public class ProfileThreadSnapshotRecord extends Record {

    public static final String INDEX_NAME = "profile_task_segment_snapshot";
    public static final String TASK_ID = "task_id";
    public static final String SEGMENT_ID = "segment_id";
    public static final String DUMP_TIME = "dump_time";
    public static final String SEQUENCE = "sequence";
    public static final String STACK_BINARY = "stack_binary";
    public static final String IS_GO = "is_go";

    @Column(name = TASK_ID)
    @SQLDatabase.CompositeIndex(withColumns = {SEGMENT_ID})
    private String taskId;
    @Column(name = SEGMENT_ID)
    @SQLDatabase.CompositeIndex(withColumns = {SEQUENCE})
    @SQLDatabase.CompositeIndex(withColumns = {DUMP_TIME})
    @BanyanDB.SeriesID(index = 0)
    private String segmentId;
    @ElasticSearch.EnableDocValues
    @Column(name = DUMP_TIME)
    private long dumpTime;
    @ElasticSearch.EnableDocValues
    @Column(name = SEQUENCE)
    private int sequence;
    @Column(name = STACK_BINARY)
    private byte[] stackBinary;
    @ElasticSearch.EnableDocValues
    @Column(name = IS_GO)
    @BanyanDB.NoIndexing
    private int isGo; // store as 0/1 for storage compatibility

    public enum Language {
        JAVA(0),
        GO(1);
        
        private final int value;
        
        Language(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public static Language fromValue(int value) {
            for (Language language : values()) {
                if (language.value == value) {
                    return language;
                }
            }
            return JAVA; // default to Java
        }
    }

    public Language getLanguage() {
        return Language.fromValue(isGo);
    }

    public void setLanguage(final Language language) {
        this.isGo = language.getValue();
    }

    public boolean isGo() {
        return isGo == Language.GO.getValue();
    }

    public void setGo(final boolean go) {
        this.isGo = go ? Language.GO.getValue() : Language.JAVA.getValue();
    }

    @Override
    public StorageID id() {
        return new StorageID()
            .append(TASK_ID, getTaskId())
            .append(SEGMENT_ID, getSegmentId())
            .append(SEQUENCE, getSequence());
    }

    public static class Builder implements StorageBuilder<ProfileThreadSnapshotRecord> {
        @Override
        public ProfileThreadSnapshotRecord storage2Entity(final Convert2Entity converter) {
            final ProfileThreadSnapshotRecord snapshot = new ProfileThreadSnapshotRecord();
            snapshot.setTaskId((String) converter.get(TASK_ID));
            snapshot.setSegmentId((String) converter.get(SEGMENT_ID));
            snapshot.setDumpTime(((Number) converter.get(DUMP_TIME)).longValue());
            snapshot.setSequence(((Number) converter.get(SEQUENCE)).intValue());
            snapshot.setTimeBucket(((Number) converter.get(TIME_BUCKET)).intValue());
            snapshot.setStackBinary(converter.getBytes(STACK_BINARY));
            final Number isGoNum = (Number) converter.get(IS_GO);
            snapshot.setLanguage(Language.fromValue(isGoNum != null ? isGoNum.intValue() : 0));
            return snapshot;
        }

        @Override
        public void entity2Storage(final ProfileThreadSnapshotRecord storageData, final Convert2Storage converter) {
            converter.accept(TASK_ID, storageData.getTaskId());
            converter.accept(SEGMENT_ID, storageData.getSegmentId());
            converter.accept(DUMP_TIME, storageData.getDumpTime());
            converter.accept(SEQUENCE, storageData.getSequence());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(STACK_BINARY, storageData.getStackBinary());
            converter.accept(IS_GO, storageData.getLanguage().getValue());
        }
    }
}
