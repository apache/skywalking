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

    /**
     * @return the underlying protobuf representation of query condition
     */
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

    /**
     * Construct a query condition with equal relation and string value, i.e. key == value
     *
     * @param key name of the field
     * @param val string value of the field
     * @return typed query condition
     */
    public static TraceSearchQuery eq(String key, String val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.EQ, val, Query.TypedPair.TypedCase.STR_PAIR);
    }

    /**
     * Construct a query condition with non-equal relation and string value, i.e. key != value
     *
     * @param key name of the field
     * @param val string value of the field
     * @return typed query condition
     */
    public static TraceSearchQuery ne(String key, String val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.NE, val, Query.TypedPair.TypedCase.STR_PAIR);
    }

    /**
     * Construct a query condition with equal relation and numeric value, i.e. key == value
     *
     * @param key name of the field
     * @param val numeric value of the field
     * @return typed query condition
     */
    public static TraceSearchQuery eq(String key, long val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.EQ, val, Query.TypedPair.TypedCase.INT_PAIR);
    }

    /**
     * Construct a query condition with non-equal relation and numeric value, i.e. key != value
     *
     * @param key name of the field
     * @param val numeric value of the field
     * @return typed query condition
     */
    public static TraceSearchQuery ne(String key, long val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.NE, val, Query.TypedPair.TypedCase.INT_PAIR);
    }

    /**
     * Construct a query condition with less-than-and-equal relation and numeric value, i.e. key <= value
     *
     * @param key name of the field
     * @param val numeric value of the field
     * @return typed query condition
     */
    public static TraceSearchQuery le(String key, long val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.LE, val, Query.TypedPair.TypedCase.INT_PAIR);
    }

    /**
     * Construct a query condition with less-than relation and numeric value, i.e. key < value
     *
     * @param key name of the field
     * @param val numeric value of the field
     * @return typed query condition
     */
    public static TraceSearchQuery lt(String key, long val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.LT, val, Query.TypedPair.TypedCase.INT_PAIR);
    }

    /**
     * Construct a query condition with greater-than-and-equal relation and numeric value, i.e. key >= value
     *
     * @param key name of the field
     * @param val numeric value of the field
     * @return typed query condition
     */
    public static TraceSearchQuery ge(String key, long val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.GE, val, Query.TypedPair.TypedCase.INT_PAIR);
    }

    /**
     * Construct a query condition with greater-than relation and numeric value, i.e. key > value
     *
     * @param key name of the field
     * @param val numeric value of the field
     * @return typed query condition
     */
    public static TraceSearchQuery gt(String key, long val) {
        return new TraceSearchQuery(key, TraceSearchRequest.BinaryOperator.GT, val, Query.TypedPair.TypedCase.INT_PAIR);
    }
}
