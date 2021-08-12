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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.apache.skywalking.banyandb.Query;

import java.util.List;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
public class TraceSearchRequest extends HasMetadata {
    // timeRange
    private final TimeRange timeRange;

    // query parameters
    @Singular
    private final List<TraceSearchQuery> queries;

    // paging parameters: limit & offset
    private final int limit;
    private final int offset;

    // query order
    private final String queryOrderField;
    private final SortOrder queryOrderSort;

    @Singular
    private final List<String> projections;

    @Builder
    @Getter
    public static class TimeRange {
        private final long startTime;
        private final long endTime;
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
