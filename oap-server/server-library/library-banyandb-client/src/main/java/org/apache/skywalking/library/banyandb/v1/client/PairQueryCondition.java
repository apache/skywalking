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
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel.Condition.MatchOption;

/**
 * PairQuery represents a query condition, including tag name, operator, and value(s);
 */
public abstract class PairQueryCondition<T> extends AbstractCriteria {
    protected final BanyandbModel.Condition.BinaryOp op;
    private final TagAndValue<T> tagAndValue;
    private final MatchOption matchOption;

    private PairQueryCondition(BanyandbModel.Condition.BinaryOp op, TagAndValue<T> tagAndValue) {
        this.op = op;
        this.tagAndValue = tagAndValue;
        this.matchOption = MatchOption.getDefaultInstance();
    }

    private PairQueryCondition(BanyandbModel.Condition.BinaryOp op,
                               TagAndValue<T> tagAndValue,
                               MatchOption matchOption) {
        this.op = op;
        this.tagAndValue = tagAndValue;
        this.matchOption = matchOption;
    }

    @Override
    public BanyandbModel.Criteria build() {
        BanyandbModel.Condition.Builder condition = BanyandbModel.Condition.newBuilder()
                                                                           .setName(this.tagAndValue.getTagName())
                                                                           .setOp(this.op)
                                                                           .setValue(this.tagAndValue.buildTypedTagValue());
        if (this.matchOption != null) {
            condition.setMatchOption(this.matchOption);
        }
        return BanyandbModel.Criteria.newBuilder()
                .setCondition(condition.build())
                .build();
    }

    public String getTagName() {
        return this.tagAndValue.getTagName();
    }

    /**
     * LongQueryCondition represents `tag(Long) $op value` condition.
     */
    public static class LongQueryCondition extends PairQueryCondition<Long> {
        private LongQueryCondition(String tagName, BanyandbModel.Condition.BinaryOp op, Long value) {
            super(op, new TagAndValue.LongTagPair(tagName, value));
        }

        /**
         * Build a query condition for {@link Long} type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_EQ} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `Long == value`
         */
        public static PairQueryCondition<Long> eq(String tagName, Long val) {
            return new LongQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_EQ, val);
        }

        /**
         * Build a query condition for {@link Long} type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_NE} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `Long != value`
         */
        public static PairQueryCondition<Long> ne(String tagName, Long val) {
            return new LongQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_NE, val);
        }

        /**
         * Build a query condition for {@link Long} type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_GT} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `Long &gt; value`
         */
        public static PairQueryCondition<Long> gt(String tagName, Long val) {
            return new LongQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_GT, val);
        }

        /**
         * Build a query condition for {@link Long} type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_GE} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `Long &ge; value`
         */
        public static PairQueryCondition<Long> ge(String tagName, Long val) {
            return new LongQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_GE, val);
        }

        /**
         * Build a query condition for {@link Long} type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_LT} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `Long &lt; value`
         */
        public static PairQueryCondition<Long> lt(String tagName, Long val) {
            return new LongQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_LT, val);
        }

        /**
         * Build a query condition for {@link Long} type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_LE} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `Long &le; value`
         */
        public static PairQueryCondition<Long> le(String tagName, Long val) {
            return new LongQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_LE, val);
        }

    }

    /**
     * StringQueryCondition represents `tag(String) $op value` condition.
     */
    public static class StringQueryCondition extends PairQueryCondition<String> {
        private StringQueryCondition(String tagName, BanyandbModel.Condition.BinaryOp op, String value) {
            super(op, new TagAndValue.StringTagPair(tagName, value));
        }

        private StringQueryCondition(String tagName, BanyandbModel.Condition.BinaryOp op, String value, MatchOption matchOption) {
            super(op, new TagAndValue.StringTagPair(tagName, value), matchOption);
        }

        /**
         * Build a query condition for {@link String} type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_EQ} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `String == value`
         */
        public static PairQueryCondition<String> eq(String tagName, String val) {
            return new StringQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_EQ, val);
        }

        /**
         * Build a query condition for {@link String} type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_NE} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `String != value`
         */
        public static PairQueryCondition<String> ne(String tagName, String val) {
            return new StringQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_NE, val);
        }

        /**
         * Build a query condition for {@link String} type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_MATCH} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `String match value`
         */
        public static PairQueryCondition<String> match(String tagName, String val) {
            return new StringQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_MATCH, val);
        }

        /**
         * Build a query condition for {@link String} type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_MATCH} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @param matchOption set the specific match options
         * @return a query that `match value`
         */
        public static PairQueryCondition<String> match(String tagName, String val, MatchOption matchOption) {
            return new StringQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_MATCH, val, matchOption);
        }
    }

    /**
     * StringArrayQueryCondition represents `tag(List of String) $op value` condition.
     */
    public static class StringArrayQueryCondition extends PairQueryCondition<List<String>> {
        private StringArrayQueryCondition(String tagName, BanyandbModel.Condition.BinaryOp op, List<String> value) {
            super(op, new TagAndValue.StringArrayTagPair(tagName, value));
        }

        /**
         * Build a query condition for {@link List} of {@link String} as the type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_EQ} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `[String] == values`
         */
        public static PairQueryCondition<List<String>> eq(String tagName, List<String> val) {
            return new StringArrayQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_EQ, val);
        }

        /**
         * Build a query condition for {@link List} of {@link String} as the type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_NE} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `[String] != values`
         */
        public static PairQueryCondition<List<String>> ne(String tagName, List<String> val) {
            return new StringArrayQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_NE, val);
        }

        /**
         * Build a query condition for {@link List} of {@link String} as the type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_HAVING} as the relation,
         * i.e. val is a subset.
         *
         * @param tagName name of the tag
         * @param val     value of the tag, a subset of the tagName's value
         * @return a query that `[String] having values`
         */
        public static PairQueryCondition<List<String>> having(String tagName, List<String> val) {
            return new StringArrayQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_HAVING, val);
        }

        /**
         * Build a query condition for {@link List} of {@link String} as the type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_NOT_HAVING} as the relation,
         * i.e. val is not a subset.
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `[String] not having values`
         */
        public static PairQueryCondition<List<String>> notHaving(String tagName, List<String> val) {
            return new StringArrayQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_NOT_HAVING, val);
        }

        /**
         * Build a query condition for {@link List} of {@link String} as the type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_IN} as the relation,
         * i.e. intersection of the val and the stored tagName's value must not be empty.
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `[String] in values`
         */
        public static PairQueryCondition<List<String>> in(String tagName, List<String> val) {
            return new StringArrayQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_IN, val);
        }

        /**
         * Build a query condition for {@link List} of {@link String} as the type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_NOT_IN} as the relation,
         * i.e. intersection of the val and the stored tagName's value must be empty.
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `[String] not in values`
         */
        public static PairQueryCondition<List<String>> notIn(String tagName, List<String> val) {
            return new StringArrayQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_NOT_IN, val);
        }
    }

    /**
     * LongArrayQueryCondition represents `tag(List of Long) $op value` condition.
     */
    public static class LongArrayQueryCondition extends PairQueryCondition<List<Long>> {
        private LongArrayQueryCondition(String tagName, BanyandbModel.Condition.BinaryOp op, List<Long> value) {
            super(op, new TagAndValue.LongArrayTagPair(tagName, value));
        }

        /**
         * Build a query condition for {@link List} of {@link Long} as the type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_EQ} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `[Long] == value`
         */
        public static PairQueryCondition<List<Long>> eq(String tagName, List<Long> val) {
            return new LongArrayQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_EQ, val);
        }

        /**
         * Build a query condition for {@link List} of {@link Long} as the type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_NE} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `[Long] != value`
         */
        public static PairQueryCondition<List<Long>> ne(String tagName, List<Long> val) {
            return new LongArrayQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_NE, val);
        }

        /**
         * Build a query condition for {@link List} of {@link Long} as the type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_HAVING} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `[Long] having values`
         */
        public static PairQueryCondition<List<Long>> having(String tagName, List<Long> val) {
            return new LongArrayQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_HAVING, val);
        }

        /**
         * Build a query condition for {@link List} of {@link Long} as the type
         * and {@link BanyandbModel.Condition.BinaryOp#BINARY_OP_NOT_HAVING} as the relation
         *
         * @param tagName name of the tag
         * @param val     value of the tag
         * @return a query that `[Long] not having values`
         */
        public static PairQueryCondition<List<Long>> notHaving(String tagName, List<Long> val) {
            return new LongArrayQueryCondition(tagName, BanyandbModel.Condition.BinaryOp.BINARY_OP_NOT_HAVING, val);
        }
    }
}
