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

package org.apache.skywalking.banyandb.v1.client;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.banyandb.v1.Banyandb;
import org.apache.skywalking.banyandb.v1.trace.BanyandbTrace;

/**
 * TraceQuery is the high-level query API for the trace model.
 */
@Setter
public class TraceQuery {
    /**
     * Owner name current entity
     */
    private final String name;
    /**
     * The time range for query.
     */
    private final TimestampRange timestampRange;
    /**
     * The projections of query result. These should have defined in the schema.
     */
    private final List<String> projections;
    /**
     * Query conditions.
     */
    private final List<PairQueryCondition<?>> conditions;
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

    public TraceQuery(final String name, final TimestampRange timestampRange, final List<String> projections) {
        this.name = name;
        this.timestampRange = timestampRange;
        this.projections = projections;
        this.conditions = new ArrayList<>(10);
        this.offset = 0;
        this.limit = 20;
    }

    /**
     * @param group The instance name.
     * @return QueryRequest for gRPC level query.
     */
    BanyandbTrace.QueryRequest build(String group) {
        final BanyandbTrace.QueryRequest.Builder builder = BanyandbTrace.QueryRequest.newBuilder();
        builder.setMetadata(Banyandb.Metadata.newBuilder()
                .setGroup(group)
                .setName(name)
                .build());
        builder.setTimeRange(timestampRange.build());
        builder.setProjection(Banyandb.Projection.newBuilder().addAllKeyNames(projections).build());
        conditions.forEach(pairQueryCondition -> builder.addFields(pairQueryCondition.build()));
        builder.setOffset(offset);
        builder.setLimit(limit);
        if (orderBy != null) {
            builder.setOrderBy(orderBy.build());
        }
        return builder.build();
    }

    @RequiredArgsConstructor
    public static class OrderBy {
        /**
         * The field name for ordering.
         */
        private final String fieldName;
        /**
         * The type of ordering.
         */
        private final Type type;

        private Banyandb.QueryOrder build() {
            final Banyandb.QueryOrder.Builder builder = Banyandb.QueryOrder.newBuilder();
            builder.setKeyName(fieldName);
            builder.setSort(
                    Type.DESC.equals(type) ? Banyandb.QueryOrder.Sort.SORT_DESC : Banyandb.QueryOrder.Sort.SORT_ASC);
            return builder.build();
        }

        public enum Type {
            ASC, DESC
        }
    }
}
