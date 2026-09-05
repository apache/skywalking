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

package org.apache.skywalking.oap.server.core.analysis.manual.aiagent;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

/**
 * One Session Flow (<code>.sf</code>) round of an AI agent conversation, stored verbatim.
 *
 * <p>A conversation is an append-only chain of rounds; this row is one of them. The series id is the sender,
 * <code>(service, instance)</code>, and <code>conversation</code> is indexed, so the list page reads rounds by
 * sender and time and a conversation read selects its chain. The stored-only columns exist for one reason: the
 * list page shows them without opening a body. They are the conversation's title and counts as of this round,
 * stamped by the sender, which holds the fold. The chain fields, previous digest, seq window and input digest,
 * are on the body's header line and are read from there.
 *
 * <p>The timestamp is the conversation's last activity as of this round, so the newest row per conversation is
 * the head and a time window on the list means "active in this window".
 */
@Getter
@Setter
@SuperDataset
@ScopeDeclaration(id = DefaultScopeDefine.AI_AGENT_SESSION_FLOW, name = "AIAgentSessionFlow")
@Stream(name = AIAgentSessionFlowRecord.INDEX_NAME, scopeId = DefaultScopeDefine.AI_AGENT_SESSION_FLOW,
    builder = AIAgentSessionFlowRecord.Builder.class, processor = RecordStreamProcessor.class)
@BanyanDB.TimestampColumn(AIAgentSessionFlowRecord.TIMESTAMP)
@BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS_AI_AGENT)
public class AIAgentSessionFlowRecord extends Record {
    public static final String INDEX_NAME = "ai_agent_session_flow";
    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_INSTANCE_ID = "service_instance_id";
    public static final String CONVERSATION = "conversation";
    public static final String ROUND = "round";
    public static final String SESSION_FROM_TIME = "session_from_time";
    public static final String TITLE = "title";
    public static final int TITLE_MAX_LENGTH = 1024;
    public static final String TALKS = "talks";
    public static final String STEPS = "steps";
    public static final String STREAMS = "streams";
    public static final String SEGMENTS = "segments";
    public static final String UNRESOLVED = "unresolved";
    public static final String DIGEST = "digest";
    /** The name of the id fragment that hashes the owner; not a column. */
    public static final String OWNER = "owner";
    public static final String TIMESTAMP = "timestamp";
    public static final String BODY = "body";

    @Column(name = SERVICE_ID)
    @BanyanDB.SeriesID(index = 0)
    private String serviceId;
    @Column(name = SERVICE_INSTANCE_ID, length = 512)
    @BanyanDB.SeriesID(index = 1)
    private String serviceInstanceId;
    @Column(name = CONVERSATION, length = 256)
    private String conversation;
    @ElasticSearch.EnableDocValues
    @Column(name = ROUND)
    private long round;
    @Column(name = SESSION_FROM_TIME, storageOnly = true)
    private long sessionFromTime;
    @Column(name = TITLE, length = TITLE_MAX_LENGTH, storageOnly = true)
    private String title;
    @Column(name = TALKS, storageOnly = true)
    private long talks;
    @Column(name = STEPS, storageOnly = true)
    private long steps;
    @Column(name = STREAMS, storageOnly = true)
    private long streams;
    @Column(name = SEGMENTS, storageOnly = true)
    private long segments;
    @Column(name = UNRESOLVED, storageOnly = true)
    private long unresolved;
    @Column(name = DIGEST, length = 64, storageOnly = true)
    private String digest;
    @ElasticSearch.EnableDocValues
    @Column(name = TIMESTAMP)
    private long timestamp;
    @Column(name = BODY, storageOnly = true)
    private byte[] body;

    /**
     * The sender owns the row: the same round pushed under another service or another sender is another row,
     * while the same sender pushing it again lands on the same one. The owner is hashed, because a service and
     * an instance name can each be long enough for the two ids to overrun a storage's id length.
     */
    @Override
    public StorageID id() {
        return new StorageID().append(OWNER, AIAgentSessionDataRecord.ownerHash(serviceId, serviceInstanceId))
                              .append(DIGEST, digest);
    }

    public static class Builder implements StorageBuilder<AIAgentSessionFlowRecord> {
        @Override
        public AIAgentSessionFlowRecord storage2Entity(final Convert2Entity converter) {
            final AIAgentSessionFlowRecord record = new AIAgentSessionFlowRecord();
            record.setServiceId((String) converter.get(SERVICE_ID));
            record.setServiceInstanceId((String) converter.get(SERVICE_INSTANCE_ID));
            record.setConversation((String) converter.get(CONVERSATION));
            record.setRound(((Number) converter.get(ROUND)).longValue());
            record.setSessionFromTime(((Number) converter.get(SESSION_FROM_TIME)).longValue());
            record.setTitle((String) converter.get(TITLE));
            record.setTalks(((Number) converter.get(TALKS)).longValue());
            record.setSteps(((Number) converter.get(STEPS)).longValue());
            record.setStreams(((Number) converter.get(STREAMS)).longValue());
            record.setSegments(((Number) converter.get(SEGMENTS)).longValue());
            record.setUnresolved(((Number) converter.get(UNRESOLVED)).longValue());
            record.setDigest((String) converter.get(DIGEST));
            record.setTimestamp(((Number) converter.get(TIMESTAMP)).longValue());
            record.setBody(converter.getBytes(BODY));
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return record;
        }

        @Override
        public void entity2Storage(final AIAgentSessionFlowRecord record, final Convert2Storage converter) {
            converter.accept(SERVICE_ID, record.getServiceId());
            converter.accept(SERVICE_INSTANCE_ID, record.getServiceInstanceId());
            converter.accept(CONVERSATION, record.getConversation());
            converter.accept(ROUND, record.getRound());
            converter.accept(SESSION_FROM_TIME, record.getSessionFromTime());
            converter.accept(TITLE, record.getTitle());
            converter.accept(TALKS, record.getTalks());
            converter.accept(STEPS, record.getSteps());
            converter.accept(STREAMS, record.getStreams());
            converter.accept(SEGMENTS, record.getSegments());
            converter.accept(UNRESOLVED, record.getUnresolved());
            converter.accept(DIGEST, record.getDigest());
            converter.accept(TIMESTAMP, record.getTimestamp());
            converter.accept(BODY, record.getBody());
            converter.accept(TIME_BUCKET, record.getTimeBucket());
        }
    }
}
