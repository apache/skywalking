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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.InvalidReferenceException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.MetadataCache;

public abstract class AbstractQuery<T> {
    /**
     * Group of the current entity
     */
    protected final List<String> groups;
    /**
     * Owner name of the current entity
     */
    protected final String name;
    /**
     * The time range for query.
     */
    protected final TimestampRange timestampRange;
    /**
     * Query conditions.
     */
    protected final List<LogicalExpression> conditions;
    /**
     * The projections of query result.
     * These should have defined in the schema.
     */
    protected final Set<String> tagProjections;

    /**
     * Query criteria.
     */
    protected AbstractCriteria criteria;

    /**
     * Enable or disable trace.
     */
    protected boolean trace;

    public AbstractQuery(List<String> groups, String name, TimestampRange timestampRange, Set<String> tagProjections) {
        this.groups = groups;
        this.name = name;
        this.timestampRange = timestampRange;
        this.conditions = new ArrayList<>(10);
        this.tagProjections = tagProjections;
    }

    /**
     * Fluent API for appending a and
     *
     * @param condition the query condition to be appended
     */
    public AbstractQuery<T> and(PairQueryCondition<?> condition) {
        this.conditions.add(new LogicalExpression(BanyandbModel.LogicalExpression.LogicalOp.LOGICAL_OP_AND, condition));
        return this;
    }

    /**
     * Fluent API for appending or
     *
     * @param condition the query condition to be appended
     */
    public AbstractQuery<T> or(PairQueryCondition<?> condition) {
        this.conditions.add(new LogicalExpression(BanyandbModel.LogicalExpression.LogicalOp.LOGICAL_OP_OR, condition));
        return this;
    }

    /**
     * Fluent API for appending query criteria
     *
     * @param criteria the query criteria to be appended
     */
    public AbstractQuery<T> criteria(AbstractCriteria criteria) {
        this.criteria = criteria;
        return this;
    }

    /**
     * Enable trace for the query.
     */
    public AbstractQuery<T> enableTrace() {
        this.trace = true;
        return this;
    }

    /**
     * @return QueryRequest for gRPC level query.
     * @throws BanyanDBException thrown from entity build, e.g. invalid reference to non-exist fields or tags.
     */
    abstract T build(MetadataCache.EntityMetadata entityMetadata) throws BanyanDBException;

    protected Optional<BanyandbModel.Criteria> buildCriteria() {
        if (criteria != null) {
            return Optional.of(criteria.build());
        }
        if (conditions.isEmpty()) {
            return Optional.empty();
        }
        if (conditions.size() == 1) {
            return Optional.of(conditions.get(0).getCond().build());
        }
        return Optional.of(conditions.stream()
                .reduce(null, (criteria, logicalExpression) -> {
                    BanyandbModel.LogicalExpression.Builder b = BanyandbModel.LogicalExpression.newBuilder();
                    if (criteria != null) {
                        b.setRight(criteria);
                    }
                    return BanyandbModel.Criteria.newBuilder()
                            .setLe(b.setOp(logicalExpression.getOp())
                                    .setLeft(logicalExpression.getCond().build())).build();
                }, (first, second) -> second));
    }

    protected BanyandbModel.TagProjection buildTagProjections(MetadataCache.EntityMetadata entityMetadata) throws BanyanDBException {
        return this.buildTagProjections(entityMetadata, this.tagProjections);
    }

    protected BanyandbModel.TagProjection buildTagProjections(MetadataCache.EntityMetadata entityMetadata, Iterable<String> tagProjections) throws BanyanDBException {
        final ListMultimap<String, String> projectionMap = ArrayListMultimap.create();
        for (final String tagName : tagProjections) {
            final Optional<MetadataCache.TagInfo> tagInfo = entityMetadata.findTagInfo(tagName);
            if (!tagInfo.isPresent()) {
                throw InvalidReferenceException.fromInvalidTag(tagName);
            }
            projectionMap.put(tagInfo.get().getTagFamilyName(), tagName);
        }

        final BanyandbModel.TagProjection.Builder b = BanyandbModel.TagProjection.newBuilder();
        for (final String tagFamilyName : projectionMap.keySet()) {
            b.addTagFamilies(BanyandbModel.TagProjection.TagFamily.newBuilder()
                    .setName(tagFamilyName)
                    .addAllTags(projectionMap.get(tagFamilyName))
                    .build());
        }
        return b.build();
    }

    public static class OrderBy {
        /**
         * The field name for ordering.
         */
        private final String indexRuleName;
        /**
         * The type of ordering.
         */
        private final Sort type;

        /**
         * Create an orderBy condition with given rule name and sort type
         */
        public OrderBy(final String indexRuleName, final Sort type) {
            this.indexRuleName = indexRuleName;
            this.type = type;
        }

        /**
         * Create an orderBy condition with timestamp and sort type
         */
        public OrderBy(final Sort type) {
            this.indexRuleName = null;
            this.type = type;
        }

        BanyandbModel.QueryOrder build() {
            final BanyandbModel.QueryOrder.Builder builder = BanyandbModel.QueryOrder.newBuilder();
            if (indexRuleName != null) {
                builder.setIndexRuleName(indexRuleName);
            }
            builder.setSort(
                Sort.DESC.equals(type) ? BanyandbModel.Sort.SORT_DESC : BanyandbModel.Sort.SORT_ASC);
            return builder.build();
        }
    }

    @RequiredArgsConstructor
    @Getter(AccessLevel.PROTECTED)
    public enum Sort {
        UNSPECIFIED, ASC, DESC;
    }

    @AllArgsConstructor
    @Getter
    static class LogicalExpression {
        private final BanyandbModel.LogicalExpression.LogicalOp op;

        private final PairQueryCondition<?> cond;
    }
}
