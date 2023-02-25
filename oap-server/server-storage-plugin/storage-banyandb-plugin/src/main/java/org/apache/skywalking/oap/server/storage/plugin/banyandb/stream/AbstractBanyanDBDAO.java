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

import org.apache.skywalking.banyandb.v1.client.AbstractCriteria;
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.And;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.Or;
import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.banyandb.v1.client.TopNQuery;
import org.apache.skywalking.banyandb.v1.client.TopNQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

public abstract class AbstractBanyanDBDAO extends AbstractDAO<BanyanDBStorageClient> {
    private static final Instant UPPER_BOUND = Instant.ofEpochSecond(0, Long.MAX_VALUE);

    private static final TimestampRange LARGEST_TIME_RANGE = new TimestampRange(0, UPPER_BOUND.toEpochMilli());

    protected AbstractBanyanDBDAO(BanyanDBStorageClient client) {
        super(client);
    }

    protected StreamQueryResponse query(String streamModelName, Set<String> tags, QueryBuilder<StreamQuery> builder) throws IOException {
        return this.query(streamModelName, tags, null, builder);
    }

    protected StreamQueryResponse query(String streamModelName, Set<String> tags, TimestampRange timestampRange,
                                        QueryBuilder<StreamQuery> builder) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findRecordMetadata(streamModelName);
        if (schema == null) {
            throw new IllegalArgumentException("schema is not registered");
        }
        final StreamQuery query;
        if (timestampRange == null) {
            query = new StreamQuery(schema.getMetadata().getGroup(), schema.getMetadata().name(), LARGEST_TIME_RANGE, tags);
        } else {
            query = new StreamQuery(schema.getMetadata().getGroup(), schema.getMetadata().name(), timestampRange, tags);
        }

        builder.apply(query);

        return getClient().query(query);
    }

    protected MeasureQueryResponse query(String measureModelName, Set<String> tags, Set<String> fields,
                                         QueryBuilder<MeasureQuery> builder) throws IOException {
        return this.query(measureModelName, tags, fields, null, builder);
    }

    protected TopNQueryResponse topN(MetadataRegistry.Schema schema, TimestampRange timestampRange, int number) throws IOException {
        final TopNQuery q = new TopNQuery(schema.getMetadata().getGroup(), schema.getTopNSpec().getName(),
                timestampRange,
                number, AbstractQuery.Sort.DESC);
        q.setAggregationType(MeasureQuery.Aggregation.Type.MEAN);
        return getClient().query(q);
    }

    protected TopNQueryResponse bottomN(MetadataRegistry.Schema schema, TimestampRange timestampRange, int number) throws IOException {
        final TopNQuery q = new TopNQuery(schema.getMetadata().getGroup(), schema.getTopNSpec().getName(),
                timestampRange,
                number, AbstractQuery.Sort.ASC);
        q.setAggregationType(MeasureQuery.Aggregation.Type.MEAN);
        return getClient().query(q);
    }

    protected MeasureQueryResponse query(String measureModelName, Set<String> tags, Set<String> fields,
                                         TimestampRange timestampRange, QueryBuilder<MeasureQuery> builder) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(measureModelName, DownSampling.Minute);
        if (schema == null) {
            throw new IllegalArgumentException("measure is not registered");
        }
        return this.query(schema, tags, fields, timestampRange, builder);
    }

    protected MeasureQueryResponse query(MetadataRegistry.Schema schema, Set<String> tags, Set<String> fields,
                                         TimestampRange timestampRange, QueryBuilder<MeasureQuery> builder) throws IOException {
        final MeasureQuery query;
        if (timestampRange == null) {
            query = new MeasureQuery(schema.getMetadata().getGroup(), schema.getMetadata().name(), LARGEST_TIME_RANGE, tags, fields);
        } else {
            query = new MeasureQuery(schema.getMetadata().getGroup(), schema.getMetadata().name(), timestampRange, tags, fields);
        }

        builder.apply(query);

        return getClient().query(query);
    }

    protected static QueryBuilder<MeasureQuery> emptyMeasureQuery() {
        return new QueryBuilder<MeasureQuery>() {
            @Override
            protected void apply(MeasureQuery query) {
            }
        };
    }

    protected abstract static class QueryBuilder<T extends AbstractQuery<? extends com.google.protobuf.GeneratedMessageV3>> {
        protected abstract void apply(final T query);

        protected PairQueryCondition<Long> eq(String name, long value) {
            return PairQueryCondition.LongQueryCondition.eq(name, value);
        }

        protected PairQueryCondition<List<String>> having(String name, List<String> value) {
            return PairQueryCondition.StringArrayQueryCondition.having(name, value);
        }

        protected PairQueryCondition<Long> lte(String name, long value) {
            return PairQueryCondition.LongQueryCondition.le(name, value);
        }

        protected PairQueryCondition<Long> gte(String name, long value) {
            return PairQueryCondition.LongQueryCondition.ge(name, value);
        }

        protected PairQueryCondition<Long> gt(String name, long value) {
            return PairQueryCondition.LongQueryCondition.gt(name, value);
        }

        protected PairQueryCondition<String> eq(String name, String value) {
            return PairQueryCondition.StringQueryCondition.eq(name, value);
        }

        protected PairQueryCondition<List<String>> in(String name, List<String> values) {
            return PairQueryCondition.StringArrayQueryCondition.in(name, values);
        }

        protected PairQueryCondition<List<String>> notIn(String name, List<String> values) {
            return PairQueryCondition.StringArrayQueryCondition.in(name, values);
        }

        protected PairQueryCondition<Long> ne(String name, long value) {
            return PairQueryCondition.LongQueryCondition.ne(name, value);
        }

        protected AbstractQuery.OrderBy desc(String name) {
            return new AbstractQuery.OrderBy(name, AbstractQuery.Sort.DESC);
        }

        protected AbstractQuery.OrderBy asc(String name) {
            return new AbstractQuery.OrderBy(name, AbstractQuery.Sort.ASC);
        }

        protected AbstractCriteria and(List<? extends AbstractCriteria> conditions) {
            if (conditions.isEmpty()) {
                return null;
            }
            if (conditions.size() == 1) {
                return conditions.get(0);
            }

            return conditions.subList(2, conditions.size()).stream().reduce(
                    And.create(conditions.get(0), conditions.get(1)),
                    (BiFunction<AbstractCriteria, AbstractCriteria, AbstractCriteria>) And::create,
                    And::create);
        }

        protected AbstractCriteria or(List<? extends AbstractCriteria> conditions) {
            if (conditions.isEmpty()) {
                return null;
            }
            if (conditions.size() == 1) {
                return conditions.get(0);
            }
            return conditions.subList(2, conditions.size()).stream().reduce(
                    Or.create(conditions.get(0), conditions.get(1)),
                    (BiFunction<AbstractCriteria, AbstractCriteria, AbstractCriteria>) Or::create,
                    Or::create);
        }
    }
}
