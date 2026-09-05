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

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
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
 * One Session Data (<code>.sd</code>) file of an AI agent conversation, stored verbatim.
 *
 * <p>A file belongs to one session and is named within it by <code>seq</code>. The row keeps only what a
 * read filters on: the series id <code>(service, instance, session)</code> and <code>seq</code>. Everything else
 * about the file, its kind, stream or run, time range and schema, is on the body's header line, and the
 * closing line carries the digest; readers decode those lines rather than duplicating them as columns.
 *
 * <p>The timestamp is the record time range's end of the file, or the session's latest record time known to
 * the sender when the file carries no timed record, so a read bounded by the conversation's range finds every
 * file a round consumed.
 */
@Getter
@Setter
@SuperDataset
@ScopeDeclaration(id = DefaultScopeDefine.AI_AGENT_SESSION_DATA, name = "AIAgentSessionData")
@Stream(name = AIAgentSessionDataRecord.INDEX_NAME, scopeId = DefaultScopeDefine.AI_AGENT_SESSION_DATA,
    builder = AIAgentSessionDataRecord.Builder.class, processor = RecordStreamProcessor.class)
@BanyanDB.TimestampColumn(AIAgentSessionDataRecord.TIMESTAMP)
@BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS_AI_AGENT)
public class AIAgentSessionDataRecord extends Record {
    public static final String INDEX_NAME = "ai_agent_session_data";
    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_INSTANCE_ID = "service_instance_id";
    public static final String SESSION = "session";
    public static final String SEQ = "seq";
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
    @Column(name = SESSION, length = 256)
    @BanyanDB.SeriesID(index = 2)
    private String session;
    @ElasticSearch.EnableDocValues
    @Column(name = SEQ)
    private long seq;
    @Column(name = DIGEST, length = 64, storageOnly = true)
    private String digest;
    @ElasticSearch.EnableDocValues
    @Column(name = TIMESTAMP)
    private long timestamp;
    @Column(name = BODY, storageOnly = true)
    private byte[] body;

    /**
     * The sender owns the row: the same file pushed under another service or another sender is another row,
     * while the same sender pushing it again lands on the same one. The owner is hashed, because a service and
     * an instance name can each be long enough for the two ids to overrun a storage's id length.
     */
    @Override
    public StorageID id() {
        return new StorageID().append(OWNER, ownerHash(serviceId, serviceInstanceId)).append(DIGEST, digest);
    }

    /**
     * @param serviceId         the service
     * @param serviceInstanceId the sender
     * @return the sha256 hex of the two, the owner half of a row's id
     */
    public static String ownerHash(final String serviceId, final String serviceInstanceId) {
        return Hashing.sha256().hashString(serviceId + Const.ID_CONNECTOR + serviceInstanceId, StandardCharsets.UTF_8).toString();
    }

    public static class Builder implements StorageBuilder<AIAgentSessionDataRecord> {
        @Override
        public AIAgentSessionDataRecord storage2Entity(final Convert2Entity converter) {
            final AIAgentSessionDataRecord record = new AIAgentSessionDataRecord();
            record.setServiceId((String) converter.get(SERVICE_ID));
            record.setServiceInstanceId((String) converter.get(SERVICE_INSTANCE_ID));
            record.setSession((String) converter.get(SESSION));
            record.setSeq(((Number) converter.get(SEQ)).longValue());
            record.setDigest((String) converter.get(DIGEST));
            record.setTimestamp(((Number) converter.get(TIMESTAMP)).longValue());
            record.setBody(converter.getBytes(BODY));
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return record;
        }

        @Override
        public void entity2Storage(final AIAgentSessionDataRecord record, final Convert2Storage converter) {
            converter.accept(SERVICE_ID, record.getServiceId());
            converter.accept(SERVICE_INSTANCE_ID, record.getServiceInstanceId());
            converter.accept(SESSION, record.getSession());
            converter.accept(SEQ, record.getSeq());
            converter.accept(DIGEST, record.getDigest());
            converter.accept(TIMESTAMP, record.getTimestamp());
            converter.accept(BODY, record.getBody());
            converter.accept(TIME_BUCKET, record.getTimeBucket());
        }
    }
}
