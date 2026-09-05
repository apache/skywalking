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

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.skywalking.library.banyandb.v1.client.RowEntity;
import org.apache.skywalking.library.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.IAIAgentConversationQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;

/**
 * Both reads are series lookups: the rounds by <code>(service, instance)</code> with <code>conversation</code> as an
 * indexed tag, the files by <code>(service, instance, session)</code> with a range on the indexed <code>seq</code>.
 * A missing instance is a partial series match, the way the log query works by service alone.
 *
 * <p>A read that is not bound to a duration covers every retained stage: the default stages and, when the group
 * has one, the cold stage, in two queries merged here. The list page alone follows its duration's stage.
 */
public class BanyanDBAIAgentConversationQueryDAO extends AbstractBanyanDBDAO implements IAIAgentConversationQueryDAO {
    private static final Set<String> ROUND_TAGS = ImmutableSet.of(
        AIAgentSessionFlowRecord.SERVICE_ID,
        AIAgentSessionFlowRecord.SERVICE_INSTANCE_ID,
        AIAgentSessionFlowRecord.CONVERSATION,
        AIAgentSessionFlowRecord.ROUND,
        AIAgentSessionFlowRecord.SESSION_FROM_TIME,
        AIAgentSessionFlowRecord.TITLE,
        AIAgentSessionFlowRecord.TALKS,
        AIAgentSessionFlowRecord.STEPS,
        AIAgentSessionFlowRecord.STREAMS,
        AIAgentSessionFlowRecord.SEGMENTS,
        AIAgentSessionFlowRecord.UNRESOLVED,
        AIAgentSessionFlowRecord.DIGEST,
        AIAgentSessionFlowRecord.TIMESTAMP
    );
    private static final Set<String> ROUND_TAGS_WITH_BODY = ImmutableSet.<String>builder()
        .addAll(ROUND_TAGS)
        .add(AIAgentSessionFlowRecord.BODY)
        .build();
    private static final Set<String> FILE_TAGS = ImmutableSet.of(
        AIAgentSessionDataRecord.SERVICE_ID,
        AIAgentSessionDataRecord.SERVICE_INSTANCE_ID,
        AIAgentSessionDataRecord.SESSION,
        AIAgentSessionDataRecord.SEQ,
        AIAgentSessionDataRecord.DIGEST,
        AIAgentSessionDataRecord.TIMESTAMP,
        AIAgentSessionDataRecord.BODY
    );
    /**
     * A file is stamped with a record time, which is at or before the moment it lands; a round with the
     * conversation's last activity. A read over "everything retained" therefore ends a little after now.
     */
    private static final long CLOCK_SKEW_MILLIS = 60_000L;

    public BanyanDBAIAgentConversationQueryDAO(final BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<AIAgentSessionFlowRecord> queryRounds(final String serviceId,
                                                      @Nullable final String serviceInstanceId,
                                                      @Nullable final String conversation,
                                                      @Nullable final Duration duration,
                                                      final int limit,
                                                      final boolean includeBody) throws IOException {
        final Conditions where = Conditions.create();
        where.eq(AIAgentSessionFlowRecord.SERVICE_ID, serviceId);
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            where.eq(AIAgentSessionFlowRecord.SERVICE_INSTANCE_ID, serviceInstanceId);
        }
        if (StringUtil.isNotEmpty(conversation)) {
            where.eq(AIAgentSessionFlowRecord.CONVERSATION, conversation);
        }
        where.orderByDesc().limit(limit);
        final Set<String> tags = includeBody ? ROUND_TAGS_WITH_BODY : ROUND_TAGS;
        final List<? extends RowEntity> rows;
        if (duration == null) {
            rows = everyStage(AIAgentSessionFlowRecord.INDEX_NAME, tags, everythingRetained(), where);
        } else {
            rows = queryDebuggable(
                duration.isColdStage(), AIAgentSessionFlowRecord.INDEX_NAME, tags, getTimestampRange(duration), where
            ).getElements();
        }
        final List<AIAgentSessionFlowRecord> rounds = rounds(rows, includeBody);
        rounds.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return rounds.size() > limit ? new ArrayList<>(rounds.subList(0, limit)) : rounds;
    }

    @Override
    public List<AIAgentSessionFlowRecord> queryRoundsByNumber(final String serviceId,
                                                              @Nullable final String serviceInstanceId,
                                                              final String conversation,
                                                              final long fromRound,
                                                              final long throughRound) throws IOException {
        final Conditions where = Conditions.create();
        where.eq(AIAgentSessionFlowRecord.SERVICE_ID, serviceId);
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            where.eq(AIAgentSessionFlowRecord.SERVICE_INSTANCE_ID, serviceInstanceId);
        }
        where.eq(AIAgentSessionFlowRecord.CONVERSATION, conversation);
        where.gte(AIAgentSessionFlowRecord.ROUND, fromRound);
        where.lte(AIAgentSessionFlowRecord.ROUND, throughRound);
        // every row of the window, up to the client's result window: a round two senders both pushed is there twice
        where.orderByAsc();
        final List<AIAgentSessionFlowRecord> rounds = rounds(
            everyStage(AIAgentSessionFlowRecord.INDEX_NAME, ROUND_TAGS_WITH_BODY, everythingRetained(), where), true);
        rounds.sort((a, b) -> Long.compare(a.getRound(), b.getRound()));
        return rounds;
    }

    private static List<AIAgentSessionFlowRecord> rounds(final List<? extends RowEntity> rows, final boolean includeBody) {
        final List<AIAgentSessionFlowRecord> rounds = new ArrayList<>(rows.size());
        for (final RowEntity row : rows) {
            final AIAgentSessionFlowRecord record = new AIAgentSessionFlowRecord();
            record.setServiceId(row.getTagValue(AIAgentSessionFlowRecord.SERVICE_ID));
            record.setServiceInstanceId(row.getTagValue(AIAgentSessionFlowRecord.SERVICE_INSTANCE_ID));
            record.setConversation(row.getTagValue(AIAgentSessionFlowRecord.CONVERSATION));
            record.setRound(longOf(row.getTagValue(AIAgentSessionFlowRecord.ROUND)));
            record.setSessionFromTime(longOf(row.getTagValue(AIAgentSessionFlowRecord.SESSION_FROM_TIME)));
            record.setTitle(row.getTagValue(AIAgentSessionFlowRecord.TITLE));
            record.setTalks(longOf(row.getTagValue(AIAgentSessionFlowRecord.TALKS)));
            record.setSteps(longOf(row.getTagValue(AIAgentSessionFlowRecord.STEPS)));
            record.setStreams(longOf(row.getTagValue(AIAgentSessionFlowRecord.STREAMS)));
            record.setSegments(longOf(row.getTagValue(AIAgentSessionFlowRecord.SEGMENTS)));
            record.setUnresolved(longOf(row.getTagValue(AIAgentSessionFlowRecord.UNRESOLVED)));
            record.setDigest(row.getTagValue(AIAgentSessionFlowRecord.DIGEST));
            final long timestamp = longOf(row.getTagValue(AIAgentSessionFlowRecord.TIMESTAMP));
            record.setTimestamp(timestamp);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
            if (includeBody) {
                record.setBody(row.getTagValue(AIAgentSessionFlowRecord.BODY));
            }
            rounds.add(record);
        }
        return rounds;
    }

    @Override
    public List<AIAgentSessionDataRecord> queryFiles(final String serviceId,
                                                     @Nullable final String serviceInstanceId,
                                                     final String session,
                                                     final long fromTimestamp,
                                                     final long toTimestamp,
                                                     final long fromSeq,
                                                     final long throughSeq) throws IOException {
        final Conditions where = Conditions.create();
        where.eq(AIAgentSessionDataRecord.SERVICE_ID, serviceId);
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            where.eq(AIAgentSessionDataRecord.SERVICE_INSTANCE_ID, serviceInstanceId);
        }
        where.eq(AIAgentSessionDataRecord.SESSION, session);
        where.gte(AIAgentSessionDataRecord.SEQ, fromSeq);
        where.lte(AIAgentSessionDataRecord.SEQ, throughSeq);
        where.orderByAsc();
        final List<? extends RowEntity> rows = everyStage(
            AIAgentSessionDataRecord.INDEX_NAME, FILE_TAGS,
            new TimestampRange(Math.max(0, fromTimestamp - 1), toTimestamp + 1), where);
        final List<AIAgentSessionDataRecord> files = new ArrayList<>(rows.size());
        for (final RowEntity row : rows) {
            final AIAgentSessionDataRecord record = new AIAgentSessionDataRecord();
            record.setServiceId(row.getTagValue(AIAgentSessionDataRecord.SERVICE_ID));
            record.setServiceInstanceId(row.getTagValue(AIAgentSessionDataRecord.SERVICE_INSTANCE_ID));
            record.setSession(row.getTagValue(AIAgentSessionDataRecord.SESSION));
            record.setSeq(longOf(row.getTagValue(AIAgentSessionDataRecord.SEQ)));
            record.setDigest(row.getTagValue(AIAgentSessionDataRecord.DIGEST));
            final long timestamp = longOf(row.getTagValue(AIAgentSessionDataRecord.TIMESTAMP));
            record.setTimestamp(timestamp);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
            record.setBody(row.getTagValue(AIAgentSessionDataRecord.BODY));
            files.add(record);
        }
        files.sort((a, b) -> Long.compare(a.getSeq(), b.getSeq()));
        return files;
    }

    private static long longOf(final Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static TimestampRange everythingRetained() {
        return new TimestampRange(0, System.currentTimeMillis() + CLOCK_SKEW_MILLIS);
    }

    /**
     * The rows of the default stages and, when the group keeps one, of the cold stage: a conversation can span
     * the two, and its list row may come from either.
     */
    private List<? extends RowEntity> everyStage(final String model, final Set<String> tags, final TimestampRange range,
                                       final Conditions where) throws IOException {
        final List<RowEntity> rows = new ArrayList<>(queryDebuggable(false, model, tags, range, where).getElements());
        final MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findRecordMetadata(model);
        if (schema != null && schema.getMetadata().getResource().isEnableColdStage()) {
            rows.addAll(queryDebuggable(true, model, tags, range, where).getElements());
        }
        return rows;
    }
}
