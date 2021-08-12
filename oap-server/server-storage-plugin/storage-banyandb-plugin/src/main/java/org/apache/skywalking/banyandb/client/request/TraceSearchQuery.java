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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.skywalking.banyandb.Query;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TraceSearchQuery {
    private final String key;
    private final TraceSearchRequest.BinaryOperator op;
    private final Object value;
    private final Query.TypedPair.TypedCase typedCase;

    public Query.PairQuery toPairQuery() {
        switch (this.typedCase) {
            case STR_PAIR:
                return Query.PairQuery.newBuilder()
                        .setOp(this.op.getOp())
                        .setCondition(Query.TypedPair.newBuilder().setStrPair(Query.StrPair.newBuilder().setKey(key).addValues((String) this.value).build()).build())
                        .build();
            case INT_PAIR:
                return Query.PairQuery.newBuilder()
                        .setOp(this.op.getOp())
                        .setCondition(Query.TypedPair.newBuilder().setIntPair(Query.IntPair.newBuilder().setKey(key).addValues((Long) this.value).build()).build())
                        .build();
            default:
                throw new IllegalStateException("should not reach here");
        }
    }

    // String
    public static TraceSearchQuery Eq(String key, String val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.EQ, val, Query.TypedPair.TypedCase.STR_PAIR);
    }

    public static TraceSearchQuery Ne(String key, String val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.NE, val, Query.TypedPair.TypedCase.STR_PAIR);
    }

    // long
    public static TraceSearchQuery Eq(String key, long val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.EQ, val, Query.TypedPair.TypedCase.INT_PAIR);
    }

    public static TraceSearchQuery Ne(String key, long val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.NE, val, Query.TypedPair.TypedCase.INT_PAIR);
    }

    public static TraceSearchQuery Le(String key, long val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.LE, val, Query.TypedPair.TypedCase.INT_PAIR);
    }

    public static TraceSearchQuery Lt(String key, long val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.LT, val, Query.TypedPair.TypedCase.INT_PAIR);
    }

    public static TraceSearchQuery Ge(String key, long val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.GE, val, Query.TypedPair.TypedCase.INT_PAIR);
    }

    public static TraceSearchQuery Gt(String key, long val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.GT, val, Query.TypedPair.TypedCase.INT_PAIR);
    }
}
