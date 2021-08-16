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

import org.apache.skywalking.banyandb.v1.Banyandb;

import java.util.List;

/**
 * PairQuery represents a query condition, including field name, operator, and value(s);
 */
public abstract class PairQueryCondition<T> extends FieldAndValue<T> {
    protected final Banyandb.PairQuery.BinaryOp op;

    private PairQueryCondition(String fieldName, Banyandb.PairQuery.BinaryOp op, T value) {
        super(fieldName, value);
        this.op = op;
    }

    Banyandb.PairQuery build() {
        return Banyandb.PairQuery.newBuilder()
                .setOp(this.op)
                .setCondition(buildTypedPair()).build();
    }

    /**
     * The various implementations should build different TypedPair
     *
     * @return typedPair to be included
     */
    protected abstract Banyandb.TypedPair buildTypedPair();

    /**
     * LongQueryCondition represents `Field(Long) $op value` condition.
     */
    public static class LongQueryCondition extends PairQueryCondition<Long> {
        private LongQueryCondition(String fieldName, Banyandb.PairQuery.BinaryOp op, Long value) {
            super(fieldName, op, value);
        }

        @Override
        protected Banyandb.TypedPair buildTypedPair() {
            return Banyandb.TypedPair.newBuilder()
                    .setKey(fieldName)
                    .setIntPair(Banyandb.Int.newBuilder()
                            .setValue(value)).build();
        }

        /**
         * Build a query condition for {@link Long} type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_EQ} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `Long == value`
         */
        public static PairQueryCondition<Long> eq(String fieldName, Long val) {
            return new LongQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_EQ, val);
        }

        /**
         * Build a query condition for {@link Long} type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_NE} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `Long != value`
         */
        public static PairQueryCondition<Long> ne(String fieldName, Long val) {
            return new LongQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_NE, val);
        }

        /**
         * Build a query condition for {@link Long} type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_GT} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `Long &gt; value`
         */
        public static PairQueryCondition<Long> gt(String fieldName, Long val) {
            return new LongQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_GT, val);
        }

        /**
         * Build a query condition for {@link Long} type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_GE} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `Long &ge; value`
         */
        public static PairQueryCondition<Long> ge(String fieldName, Long val) {
            return new LongQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_GE, val);
        }

        /**
         * Build a query condition for {@link Long} type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_LT} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `Long &lt; value`
         */
        public static PairQueryCondition<Long> lt(String fieldName, Long val) {
            return new LongQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_LT, val);
        }

        /**
         * Build a query condition for {@link Long} type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_LE} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `Long &le; value`
         */
        public static PairQueryCondition<Long> le(String fieldName, Long val) {
            return new LongQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_LE, val);
        }
    }

    /**
     * StringQueryCondition represents `Field(String) $op value` condition.
     */
    public static class StringQueryCondition extends PairQueryCondition<String> {
        private StringQueryCondition(String fieldName, Banyandb.PairQuery.BinaryOp op, String value) {
            super(fieldName, op, value);
        }

        @Override
        protected Banyandb.TypedPair buildTypedPair() {
            return Banyandb.TypedPair.newBuilder()
                    .setKey(fieldName)
                    .setStrPair(Banyandb.Str.newBuilder().setValue(value)).build();
        }

        /**
         * Build a query condition for {@link String} type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_EQ} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `String == value`
         */
        public static PairQueryCondition<String> eq(String fieldName, String val) {
            return new StringQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_EQ, val);
        }

        /**
         * Build a query condition for {@link String} type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_NE} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `String != value`
         */
        public static PairQueryCondition<String> ne(String fieldName, String val) {
            return new StringQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_NE, val);
        }
    }

    /**
     * StringArrayQueryCondition represents `Field(List of String) $op value` condition.
     */
    public static class StringArrayQueryCondition extends PairQueryCondition<List<String>> {
        private StringArrayQueryCondition(String fieldName, Banyandb.PairQuery.BinaryOp op, List<String> value) {
            super(fieldName, op, value);
        }

        @Override
        protected Banyandb.TypedPair buildTypedPair() {
            return Banyandb.TypedPair.newBuilder()
                    .setKey(fieldName)
                    .setStrArrayPair(Banyandb.StrArray.newBuilder()
                            .addAllValue(value)).build();
        }

        /**
         * Build a query condition for {@link List} of {@link String} as the type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_EQ} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `[String] == values`
         */
        public static PairQueryCondition<List<String>> eq(String fieldName, List<String> val) {
            return new StringArrayQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_EQ, val);
        }

        /**
         * Build a query condition for {@link List} of {@link String} as the type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_NE} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `[String] != values`
         */
        public static PairQueryCondition<List<String>> ne(String fieldName, List<String> val) {
            return new StringArrayQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_NE, val);
        }

        /**
         * Build a query condition for {@link List} of {@link String} as the type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_HAVING} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `[String] having values`
         */
        public static PairQueryCondition<List<String>> having(String fieldName, List<String> val) {
            return new StringArrayQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_HAVING, val);
        }

        /**
         * Build a query condition for {@link List} of {@link String} as the type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_NOT_HAVING} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `[String] not having values`
         */
        public static PairQueryCondition<List<String>> notHaving(String fieldName, List<String> val) {
            return new StringArrayQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_NOT_HAVING, val);
        }
    }

    /**
     * LongArrayQueryCondition represents `Field(List of Long) $op value` condition.
     */
    public static class LongArrayQueryCondition extends PairQueryCondition<List<Long>> {
        private LongArrayQueryCondition(String fieldName, Banyandb.PairQuery.BinaryOp op, List<Long> value) {
            super(fieldName, op, value);
        }

        @Override
        protected Banyandb.TypedPair buildTypedPair() {
            return Banyandb.TypedPair.newBuilder()
                    .setKey(fieldName)
                    .setIntArrayPair(Banyandb.IntArray.newBuilder()
                            .addAllValue(value)).build();
        }

        /**
         * Build a query condition for {@link List} of {@link Long} as the type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_EQ} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `[Long] == value`
         */
        public static PairQueryCondition<List<Long>> eq(String fieldName, List<Long> val) {
            return new LongArrayQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_EQ, val);
        }

        /**
         * Build a query condition for {@link List} of {@link Long} as the type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_NE} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `[Long] != value`
         */
        public static PairQueryCondition<List<Long>> ne(String fieldName, List<Long> val) {
            return new LongArrayQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_NE, val);
        }

        /**
         * Build a query condition for {@link List} of {@link Long} as the type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_HAVING} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `[Long] having values`
         */
        public static PairQueryCondition<List<Long>> having(String fieldName, List<Long> val) {
            return new LongArrayQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_HAVING, val);
        }

        /**
         * Build a query condition for {@link List} of {@link Long} as the type
         * and {@link Banyandb.PairQuery.BinaryOp#BINARY_OP_NOT_HAVING} as the relation
         *
         * @param fieldName name of the field
         * @param val       value of the field
         * @return a query that `[Long] not having values`
         */
        public static PairQueryCondition<List<Long>> notHaving(String fieldName, List<Long> val) {
            return new LongArrayQueryCondition(fieldName, Banyandb.PairQuery.BinaryOp.BINARY_OP_NOT_HAVING, val);
        }
    }
}
