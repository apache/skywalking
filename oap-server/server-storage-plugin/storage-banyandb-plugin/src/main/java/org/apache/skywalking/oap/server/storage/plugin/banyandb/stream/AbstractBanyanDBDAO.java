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

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

public abstract class AbstractBanyanDBDAO extends AbstractDAO<BanyanDBStorageClient> {
    private static final Instant UPPER_BOUND = Instant.ofEpochSecond(0, Long.MAX_VALUE);

    private static final TimestampRange LARGEST_TIME_RANGE = new TimestampRange(0, UPPER_BOUND.toEpochMilli());

    protected AbstractBanyanDBDAO(BanyanDBStorageClient client) {
        super(client);
    }

    protected StreamQueryResponse query(MetadataRegistry.PartialMetadata metadata, Set<String> searchableTags, QueryBuilder builder) throws IOException {
        return this.query(metadata, searchableTags, null, builder);
    }

    protected StreamQueryResponse query(MetadataRegistry.PartialMetadata metadata, Set<String> searchableTags, TimestampRange timestampRange,
                                        QueryBuilder builder) throws IOException {
        final StreamQuery query;
        if (timestampRange == null) {
            query = new StreamQuery(metadata.getGroup(), metadata.getName(), LARGEST_TIME_RANGE, searchableTags);
        } else {
            query = new StreamQuery(metadata.getGroup(), metadata.getName(), timestampRange, searchableTags);
        }

        builder.apply(query);

        return getClient().query(query);
    }

    protected abstract static class QueryBuilder {
        abstract void apply(final StreamQuery query);

        protected PairQueryCondition<Long> eq(String name, long value) {
            return PairQueryCondition.LongQueryCondition.eq(name, value);
        }

        protected PairQueryCondition<Long> lte(String name, long value) {
            return PairQueryCondition.LongQueryCondition.le(name, value);
        }

        protected PairQueryCondition<Long> gte(String name, long value) {
            return PairQueryCondition.LongQueryCondition.ge(name, value);
        }

        protected PairQueryCondition<String> eq(String name, String value) {
            return PairQueryCondition.StringQueryCondition.eq(name, value);
        }
    }
}
