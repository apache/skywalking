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
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventTraceType;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BanyanDBSpanAttachedEventQueryDAO extends AbstractBanyanDBDAO implements ISpanAttachedEventQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(SpanAttachedEventRecord.START_TIME_SECOND,
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
    private final int batchSize;

    public BanyanDBSpanAttachedEventQueryDAO(BanyanDBStorageClient client, int profileDataQueryBatchSize) {
        super(client);
        this.batchSize = profileDataQueryBatchSize;
    }

    @Override
    public List<SpanAttachedEventRecord> querySpanAttachedEvents(SpanAttachedEventTraceType type, List<String> traceIds) throws IOException {
        final StreamQueryResponse resp = queryDebuggable(SpanAttachedEventRecord.INDEX_NAME, TAGS, null, new QueryBuilder<StreamQuery>() {
            @Override
            protected void apply(StreamQuery query) {
                query.and(in(SpanAttachedEventRecord.RELATED_TRACE_ID, traceIds));
                query.and(eq(SpanAttachedEventRecord.TRACE_REF_TYPE, type.value()));
                query.setOrderBy(new StreamQuery.OrderBy(AbstractQuery.Sort.ASC));
                query.setLimit(batchSize);
            }
        });

        return resp.getElements().stream().map(this::buildRecord).collect(Collectors.toList());
    }

    private SpanAttachedEventRecord buildRecord(RowEntity row) {
        final SpanAttachedEventRecord.Builder builder = new SpanAttachedEventRecord.Builder();
        return builder.storage2Entity(new BanyanDBConverter.StorageToStream(SpanAttachedEventRecord.INDEX_NAME, row));
    }
}
