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
import org.apache.skywalking.library.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.library.banyandb.v1.client.RowEntity;
import org.apache.skywalking.library.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecoveryRecord;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.input.AlarmQueryCondition;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.EntityIdConstraint;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.alarm.AlarmRecord} is a stream,
 * which can be used to build a {@link org.apache.skywalking.oap.server.core.query.type.AlarmMessage}
 */
public class BanyanDBAlarmQueryDAO extends AbstractBanyanDBDAO implements IAlarmQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(AlarmRecord.SCOPE,
            AlarmRecord.NAME, AlarmRecord.ID0, AlarmRecord.ID1, AlarmRecord.UUID, AlarmRecord.ALARM_MESSAGE,
            AlarmRecord.START_TIME, AlarmRecord.RULE_NAME, AlarmRecord.TAGS, AlarmRecord.TAGS_RAW_DATA,
            AlarmRecord.SNAPSHOT, AlarmRecord.LAYER);
    private static final Set<String> RECOVERY_TAGS = ImmutableSet.of(
        AlarmRecoveryRecord.UUID, AlarmRecoveryRecord.RECOVERY_TIME);

    public BanyanDBAlarmQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from, Duration duration, List<Tag> tags) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        StreamQueryResponse resp = query(isColdStage, AlarmRecord.INDEX_NAME, TAGS,
                getTimestampRange(duration),
                new QueryBuilder<>() {
                    @Override
                    public void apply(StreamQuery query) {
                        if (Objects.nonNull(scopeId)) {
                            query.and(eq(AlarmRecord.SCOPE, (long) scopeId));
                        }
                        if (StringUtil.isNotEmpty(keyword)) {
                            query.and(match(AlarmRecord.ALARM_MESSAGE, keyword));
                        }
                        if (CollectionUtils.isNotEmpty(tags)) {
                            List<String> tagsConditions = new ArrayList<>(tags.size());
                            for (final Tag tag : tags) {
                                tagsConditions.add(tag.toString());
                            }
                            query.and(having(AlarmRecord.TAGS, tagsConditions));
                        }

                        query.setLimit(limit);
                        query.setOffset(from);
                        query.setOrderBy(new StreamQuery.OrderBy(AbstractQuery.Sort.DESC));
                    }
                });

        Alarms alarms = new Alarms();

        for (final RowEntity rowEntity : resp.getElements()) {
            AlarmRecord.Builder builder = new AlarmRecord.Builder();
            AlarmRecord alarmRecord = builder.storage2Entity(
                    new BanyanDBConverter.StorageToStream(AlarmRecord.INDEX_NAME, rowEntity)
            );
            AlarmMessage alarmMessage = buildAlarmMessage(alarmRecord);
            if (!CollectionUtils.isEmpty(alarmRecord.getTagsRawData())) {
                parseDataBinary(alarmRecord.getTagsRawData(), alarmMessage.getTags());
            }
            alarms.getMsgs().add(alarmMessage);
        }
        updateAlarmRecoveryTime(alarms, duration);
        return alarms;
    }

    @Override
    public Alarms queryAlarms(final AlarmQueryCondition condition, final int limit, final int from) throws IOException {
        if (condition == null || condition.getDuration() == null) {
            return new Alarms();
        }
        final Duration duration = condition.getDuration();
        final boolean isColdStage = duration.isColdStage();
        final List<EntityIdConstraint> entityConstraints = resolveEntityFilters(condition.getEntities());

        // BanyanDB lacks an OR-grouping primitive across multiple tag
        // conditions on a single stream query, so the per-entity AND/OR
        // shape cannot be pushed down in a single call. Strategy: one
        // BanyanDB query per EntityIdConstraint with strict push-down
        // (id0=X AND id1=Y where set), each query bounded by
        // limit+from rows so pagination still terminates after dedupe.
        // Results are dedupe-by-uuid'd, sorted by startTime DESC, then
        // paged in memory. Common case (1-2 entities) = 1-4 queries,
        // each cheap.
        final LinkedHashMap<String, AlarmRecord> merged = new LinkedHashMap<>();
        if (entityConstraints.isEmpty()) {
            collect(merged, isColdStage, duration, condition, null, limit, from);
        } else {
            // Per-constraint query — over-fetch (limit + from) per slice so
            // the global dedupe-sort-page step has enough data.
            final int perQueryLimit = limit + from;
            for (final EntityIdConstraint c : entityConstraints) {
                collect(merged, isColdStage, duration, condition, c, perQueryLimit, 0);
            }
        }

        final List<AlarmRecord> sorted = new ArrayList<>(merged.values());
        sorted.sort(Comparator.comparingLong(AlarmRecord::getStartTime).reversed());

        final Alarms alarms = new Alarms();
        final int start = Math.min(from, sorted.size());
        final int end = Math.min(start + limit, sorted.size());
        for (int i = start; i < end; i++) {
            final AlarmRecord alarmRecord = sorted.get(i);
            final AlarmMessage alarmMessage = buildAlarmMessage(alarmRecord);
            if (!CollectionUtils.isEmpty(alarmRecord.getTagsRawData())) {
                parseDataBinary(alarmRecord.getTagsRawData(), alarmMessage.getTags());
            }
            alarms.getMsgs().add(alarmMessage);
        }
        updateAlarmRecoveryTime(alarms, duration);
        return alarms;
    }

    /**
     * Issue a single BanyanDB stream query for the given (optional) entity
     * constraint and accumulate the results into {@code merged}, keyed by
     * alarm uuid to dedupe across overlapping per-constraint queries.
     */
    private void collect(final LinkedHashMap<String, AlarmRecord> merged,
                         final boolean isColdStage,
                         final Duration duration,
                         final AlarmQueryCondition condition,
                         final EntityIdConstraint entityConstraint,
                         final int queryLimit,
                         final int queryOffset) throws IOException {
        final StreamQueryResponse resp = query(isColdStage, AlarmRecord.INDEX_NAME, TAGS,
                getTimestampRange(duration),
                new QueryBuilder<>() {
                    @Override
                    public void apply(StreamQuery query) {
                        if (StringUtil.isNotEmpty(condition.getKeyword())) {
                            query.and(match(AlarmRecord.ALARM_MESSAGE, condition.getKeyword()));
                        }
                        if (StringUtil.isNotEmpty(condition.getLayer())) {
                            query.and(eq(AlarmRecord.LAYER, (long) Layer.nameOf(condition.getLayer()).value()));
                        }
                        if (CollectionUtils.isNotEmpty(condition.getRuleNames())) {
                            query.and(in(AlarmRecord.RULE_NAME, condition.getRuleNames()));
                        }
                        if (entityConstraint != null) {
                            if (entityConstraint.getId0() != null) {
                                query.and(eq(AlarmRecord.ID0, entityConstraint.getId0()));
                            }
                            if (entityConstraint.getId1() != null) {
                                query.and(eq(AlarmRecord.ID1, entityConstraint.getId1()));
                            }
                        }
                        if (CollectionUtils.isNotEmpty(condition.getTags())) {
                            List<String> tagsConditions = new ArrayList<>(condition.getTags().size());
                            for (final Tag tag : condition.getTags()) {
                                tagsConditions.add(tag.toString());
                            }
                            query.and(having(AlarmRecord.TAGS, tagsConditions));
                        }
                        query.setLimit(queryLimit);
                        query.setOffset(queryOffset);
                        query.setOrderBy(new StreamQuery.OrderBy(AbstractQuery.Sort.DESC));
                    }
                });
        for (final RowEntity rowEntity : resp.getElements()) {
            final AlarmRecord.Builder builder = new AlarmRecord.Builder();
            final AlarmRecord alarmRecord = builder.storage2Entity(
                    new BanyanDBConverter.StorageToStream(AlarmRecord.INDEX_NAME, rowEntity)
            );
            merged.putIfAbsent(alarmRecord.getUuid(), alarmRecord);
        }
    }

    /**
     * Post-filter helper: true if the alarm's id0/id1 satisfies any of the
     * constraints (constraint internal AND, list-wise OR). Retained for any
     * future code path that needs in-memory matching; the main query path
     * now uses strict per-constraint storage queries instead.
     */
    @SuppressWarnings("unused")
    private boolean matchesAnyConstraint(final AlarmRecord record, final List<EntityIdConstraint> constraints) {
        for (final EntityIdConstraint c : constraints) {
            final boolean id0Ok = c.getId0() == null || c.getId0().equals(record.getId0());
            final boolean id1Ok = c.getId1() == null || c.getId1().equals(record.getId1());
            if (id0Ok && id1Ok) {
                return true;
            }
        }
        return false;
    }

    private void updateAlarmRecoveryTime(Alarms alarms, Duration duration) throws IOException {
        List<AlarmMessage> alarmMessages = alarms.getMsgs();
        Map<String, AlarmRecoveryRecord> alarmRecoveryRecordMap = getAlarmRecoveryRecord(alarmMessages, duration);
        alarmMessages.forEach(alarmMessage -> {
            AlarmRecoveryRecord alarmRecoveryRecord = alarmRecoveryRecordMap.get(alarmMessage.getUuid());
            if (alarmRecoveryRecord != null) {
                alarmMessage.setRecoveryTime(alarmRecoveryRecord.getRecoveryTime());
            }
        });

    }

    private Map<String, AlarmRecoveryRecord> getAlarmRecoveryRecord(List<AlarmMessage> msgs, Duration duration) throws IOException {
        Map<String, AlarmRecoveryRecord> result = new HashMap<>();
        if (CollectionUtils.isEmpty(msgs)) {
            return result;
        }
        final boolean isColdStage = duration != null && duration.isColdStage();
        List<String> uuids = msgs.stream().map(AlarmMessage::getUuid).collect(Collectors.toList());
        StreamQueryResponse resp = query(isColdStage, AlarmRecoveryRecord.INDEX_NAME, RECOVERY_TAGS,
                getTimestampRange(duration),
                new QueryBuilder<>() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.and(in(AlarmRecoveryRecord.UUID, uuids));
                    }
                });

        for (final RowEntity rowEntity : resp.getElements()) {
            AlarmRecoveryRecord.Builder builder = new AlarmRecoveryRecord.Builder();
            AlarmRecoveryRecord alarmRecoveryRecord = builder.storage2Entity(
                    new BanyanDBConverter.StorageToStream(AlarmRecoveryRecord.INDEX_NAME, rowEntity)
            );
            result.put(alarmRecoveryRecord.getUuid(), alarmRecoveryRecord);
        }
        return result;
    }
}
