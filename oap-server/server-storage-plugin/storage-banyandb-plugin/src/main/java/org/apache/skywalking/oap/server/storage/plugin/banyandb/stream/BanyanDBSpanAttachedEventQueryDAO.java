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
import javax.annotation.Nullable;
import org.apache.skywalking.library.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.library.banyandb.v1.client.RowEntity;
import org.apache.skywalking.library.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SWSpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BanyanDBSpanAttachedEventQueryDAO extends AbstractBanyanDBDAO implements ISpanAttachedEventQueryDAO {
    private static final Set<String> ZK_TAGS = ImmutableSet.of(SpanAttachedEventRecord.START_TIME_SECOND,
        SpanAttachedEventRecord.START_TIME_NANOS,
        SpanAttachedEventRecord.EVENT,
        SpanAttachedEventRecord.END_TIME_SECOND,
        SpanAttachedEventRecord.END_TIME_NANOS,
        SpanAttachedEventRecord.TRACE_REF_TYPE,
        SpanAttachedEventRecord.RELATED_TRACE_ID,
        SpanAttachedEventRecord.TRACE_SEGMENT_ID,
        SpanAttachedEventRecord.TRACE_SPAN_ID,
        SpanAttachedEventRecord.DATA_BINARY,
        SpanAttachedEventRecord.TIMESTAMP);

    private static final Set<String> SW_TAGS = ImmutableSet.of(SWSpanAttachedEventRecord.START_TIME_SECOND,
        SWSpanAttachedEventRecord.START_TIME_NANOS,
        SWSpanAttachedEventRecord.EVENT,
        SWSpanAttachedEventRecord.END_TIME_SECOND,
        SWSpanAttachedEventRecord.END_TIME_NANOS,
        SWSpanAttachedEventRecord.TRACE_REF_TYPE,
        SWSpanAttachedEventRecord.RELATED_TRACE_ID,
        SWSpanAttachedEventRecord.TRACE_SEGMENT_ID,
        SWSpanAttachedEventRecord.TRACE_SPAN_ID,
        SWSpanAttachedEventRecord.DATA_BINARY,
        SWSpanAttachedEventRecord.TIMESTAMP);

    private final int batchSize;

    public BanyanDBSpanAttachedEventQueryDAO(BanyanDBStorageClient client, int profileDataQueryBatchSize) {
        super(client);
        this.batchSize = profileDataQueryBatchSize;
    }

    @Override
    public List<SWSpanAttachedEventRecord> querySWSpanAttachedEvents(List<String> traceIds, @Nullable Duration duration) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        final StreamQueryResponse resp = queryDebuggable(
                isColdStage, SWSpanAttachedEventRecord.INDEX_NAME, ZK_TAGS, getTimestampRange(duration),
                new QueryBuilder<StreamQuery>() {
                    @Override
                    protected void apply(StreamQuery query) {
                        query.and(in(SWSpanAttachedEventRecord.RELATED_TRACE_ID, traceIds));
                        query.setOrderBy(new StreamQuery.OrderBy(AbstractQuery.Sort.ASC));
                        query.setLimit(batchSize);
                    }
                });

        return resp.getElements().stream().map(this::buildSWRecord).collect(Collectors.toList());
    }

    @Override
    public List<SpanAttachedEventRecord> queryZKSpanAttachedEvents(List<String> traceIds, @Nullable Duration duration) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        final StreamQueryResponse resp = queryDebuggable(
            isColdStage, SpanAttachedEventRecord.INDEX_NAME, ZK_TAGS, getTimestampRange(duration),
            new QueryBuilder<StreamQuery>() {
            @Override
            protected void apply(StreamQuery query) {
                query.and(in(SpanAttachedEventRecord.RELATED_TRACE_ID, traceIds));
                query.setOrderBy(new StreamQuery.OrderBy(AbstractQuery.Sort.ASC));
                query.setLimit(batchSize);
            }
        });

        return resp.getElements().stream().map(this::buildZKRecord).collect(Collectors.toList());
    }

    private SpanAttachedEventRecord buildZKRecord(RowEntity row) {
        final SpanAttachedEventRecord.Builder builder = new SpanAttachedEventRecord.Builder();
        return builder.storage2Entity(new BanyanDBConverter.StorageToStream(SpanAttachedEventRecord.INDEX_NAME, row));
    }

    private SWSpanAttachedEventRecord buildSWRecord(RowEntity row) {
        final SWSpanAttachedEventRecord.Builder builder = new SWSpanAttachedEventRecord.Builder();
        return builder.storage2Entity(new BanyanDBConverter.StorageToStream(SWSpanAttachedEventRecord.INDEX_NAME, row));
    }
}
