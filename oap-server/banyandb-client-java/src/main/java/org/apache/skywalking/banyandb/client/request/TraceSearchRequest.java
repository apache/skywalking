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

package org.apache.skywalking.banyandb.client.request;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import org.apache.skywalking.banyandb.Query;

import java.util.List;

/**
 * A wrapped request for conditional trace search with complex query conditions
 */
@Builder
@Data
public class TraceSearchRequest {
    /**
     * time range of the entities requested
     */
    private final TimeRange timeRange;

    /**
     * conditions of the entities requested.
     * the various conditions are implicitly organized as logical "AND"
     */
    @Singular
    private final List<TraceSearchQuery> queries;

    /**
     * paging parameters
     * 1) limit: total records to be returned
     * 2) offset: record to be skipped from the beginning
     */
    private final int limit;
    private final int offset;

    /**
     * sorted field name and order used for the returned entities
     */
    private final OrderBy orderBy;

    /**
     * While searching for entities, you are able to specify fields being returned.
     * Projections must only contain valid field names defined in the schema.
     */
    @Singular
    private final List<String> projections;

    @RequiredArgsConstructor
    @Getter
    public static class TimeRange {
        /**
         * StartTime of the entity in the timeunit of seconds. Inclusive.
         */
        private final long startTime;

        /**
         * EndTime of the entity in the timeunit of seconds. Inclusive.
         */
        private final long endTime;
    }

    @RequiredArgsConstructor
    @Getter
    public static class OrderBy {
        /**
         * Sorted field name
         */
        private final String fieldName;

        /**
         * Sort order
         */
        private final SortOrder sort;
    }

    public enum SortOrder {
        DESC(Query.QueryOrder.Sort.SORT_DESC), ASC(Query.QueryOrder.Sort.SORT_ASC);

        @Getter
        private final Query.QueryOrder.Sort sort;

        SortOrder(Query.QueryOrder.Sort sort) {
            this.sort = sort;
        }
    }

    public enum BinaryOperator {
        EQ(Query.PairQuery.BinaryOp.BINARY_OP_EQ),
        NE(Query.PairQuery.BinaryOp.BINARY_OP_NE),
        LT(Query.PairQuery.BinaryOp.BINARY_OP_LT),
        LE(Query.PairQuery.BinaryOp.BINARY_OP_LE),
        GT(Query.PairQuery.BinaryOp.BINARY_OP_GT),
        GE(Query.PairQuery.BinaryOp.BINARY_OP_GE),
        HAVING(Query.PairQuery.BinaryOp.BINARY_OP_HAVING),
        NOT_HAVING(Query.PairQuery.BinaryOp.BINARY_OP_NOT_HAVING);

        @Getter
        private final Query.PairQuery.BinaryOp op;

        BinaryOperator(Query.PairQuery.BinaryOp op) {
            this.op = op;
        }
    }
}
