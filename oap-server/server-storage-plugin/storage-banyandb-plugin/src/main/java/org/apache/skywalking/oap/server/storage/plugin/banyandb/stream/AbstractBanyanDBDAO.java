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

import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public abstract class AbstractBanyanDBDAO extends AbstractDAO<BanyanDBStorageClient> {
    private static final Instant UPPER_BOUND = Instant.ofEpochSecond(0, Long.MAX_VALUE);

    private static final TimestampRange LARGEST_TIME_RANGE = new TimestampRange(0, UPPER_BOUND.toEpochMilli());

    protected AbstractBanyanDBDAO(BanyanDBStorageClient client) {
        super(client);
    }

    protected StreamQueryResponse query(String modelName, Set<String> tags, QueryBuilder<StreamQuery> builder) throws IOException {
        return this.query(modelName, tags, null, builder);
    }

    protected StreamQueryResponse query(String modelName, Set<String> tags, TimestampRange timestampRange,
                                        QueryBuilder<StreamQuery> builder) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(modelName);
        if (schema == null) {
            throw new IllegalStateException("schema is not registered");
        }
        final StreamQuery query;
        if (timestampRange == null) {
            query = new StreamQuery(schema.getMetadata().getGroup(), schema.getMetadata().getName(), LARGEST_TIME_RANGE, tags);
        } else {
            query = new StreamQuery(schema.getMetadata().getGroup(), schema.getMetadata().getName(), timestampRange, tags);
        }

        builder.apply(query);

        return getClient().query(query);
    }

    protected MeasureQueryResponse query(String modelName, Set<String> tags, Set<String> fields,
                                         QueryBuilder<MeasureQuery> builder) throws IOException {
        return this.query(modelName, tags, fields, null, builder);
    }

    protected MeasureQueryResponse query(String modelName, Set<String> tags, Set<String> fields,
                                         TimestampRange timestampRange, QueryBuilder<MeasureQuery> builder) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(modelName);
        if (schema == null) {
            throw new IllegalStateException("schema is not registered");
        }
        final MeasureQuery query;
        if (timestampRange == null) {
            query = new MeasureQuery(schema.getMetadata().getGroup(), schema.getMetadata().getName(), LARGEST_TIME_RANGE, tags, fields);
        } else {
            query = new MeasureQuery(schema.getMetadata().getGroup(), schema.getMetadata().getName(), timestampRange, tags, fields);
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

        protected PairQueryCondition<Long> ne(String name, long value) {
            return PairQueryCondition.LongQueryCondition.ne(name, value);
        }
    }
}
