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

package org.apache.skywalking.library.banyandb.v1.client;

import java.util.List;
import java.util.Set;
import lombok.Setter;
import org.apache.skywalking.banyandb.trace.v1.BanyandbTrace;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

/**
 * TraceQuery is the high-level query API for the trace model.
 */
@Setter
public class TraceQuery extends AbstractQuery<BanyandbTrace.QueryRequest> {
    /**
     * The starting row id of the query. Default value is 0.
     */
    private int offset;
    /**
     * The limit size of the query. Default value is 20.
     */
    private int limit;
    /**
     * One order condition is supported and optional.
     */
    private OrderBy orderBy;

    /**
     * The stages of the trace query.
     */
    private Set<String> stages;

    public TraceQuery(final List<String> groups, final String name, final TimestampRange timestampRange, final Set<String> projections) {
        super(groups, name, timestampRange, projections);
        this.offset = 0;
        this.limit = 20;
    }

    public TraceQuery(final List<String> groups, final String name, final Set<String> projections) {
        this(groups, name, null, projections);
    }

    @Override
    public TraceQuery and(PairQueryCondition<?> condition) {
        return (TraceQuery) super.and(condition);
    }

    @Override
    public TraceQuery or(PairQueryCondition<?> condition) {
        return (TraceQuery) super.or(condition);
    }
    
    public TraceQuery stages(Set<String> stages) {
        this.stages = stages;
        return this;
    }

    @Override
    BanyandbTrace.QueryRequest build() throws BanyanDBException {
        final BanyandbTrace.QueryRequest.Builder builder = BanyandbTrace.QueryRequest.newBuilder();
        builder.setName(this.name);
        builder.addAllGroups(this.groups);
        if (timestampRange != null) {
            builder.setTimeRange(timestampRange.build());
        }
        builder.addAllTagProjection(tagProjections.keySet());
        buildCriteria().ifPresent(builder::setCriteria);
        builder.setOffset(offset);
        builder.setLimit(limit);
        if (orderBy != null) {
            builder.setOrderBy(orderBy.build());
        }
        if (stages != null && !stages.isEmpty()) {
            builder.addAllStages(stages);
        }
        builder.setTrace(this.trace);
        return builder.build();
    }
}