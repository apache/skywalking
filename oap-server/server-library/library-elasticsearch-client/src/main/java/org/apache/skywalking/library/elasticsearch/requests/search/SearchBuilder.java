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
 */

package org.apache.skywalking.library.elasticsearch.requests.search;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.Aggregation;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.AggregationBuilder;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

public final class SearchBuilder {
    private Integer from;
    private Integer size;
    private QueryBuilder queryBuilder;
    private ImmutableList.Builder<Sort> sort;
    private Set<String> source;
    private ImmutableMap.Builder<String, Aggregation> aggregations;

    SearchBuilder() {
    }

    public SearchBuilder source(Set<String> source) {
        requireNonNull(source, "source");
        checkArgument(source.size() > 0, "source size must be > 0, but was %s", source.size());
        this.source = source;
        return this;
    }

    public SearchBuilder from(Integer from) {
        requireNonNull(from, "from");
        checkArgument(from >= 0, "from must be >= 0, but was %s", from);
        this.from = from;
        return this;
    }

    public SearchBuilder size(Integer size) {
        requireNonNull(size, "size");
        checkArgument(size >= 0, "size must be positive, but was %s", size);
        this.size = size;
        return this;
    }

    public SearchBuilder sort(String by, Sort.Order order) {
        checkArgument(!Strings.isNullOrEmpty(by), "by cannot be blank");
        requireNonNull(order, "order");
        sort().add(new Sort(by, order));
        return this;
    }

    public SearchBuilder query(QueryBuilder queryBuilder) {
        checkState(this.queryBuilder == null, "queryBuilder is already set");
        this.queryBuilder = requireNonNull(queryBuilder, "queryBuilder");
        return this;
    }

    public SearchBuilder aggregation(Aggregation aggregation) {
        requireNonNull(aggregation, "aggregation");
        aggregations().put(aggregation.name(), aggregation);
        return this;
    }

    public SearchBuilder aggregation(AggregationBuilder builder) {
        requireNonNull(builder, "builder");
        return aggregation(builder.build());
    }

    public Search build() {
        final Sorts sorts;
        if (sort == null) {
            sorts = null;
        } else {
            sorts = new Sorts(sort.build());
        }

        final ImmutableMap<String, Aggregation> aggregations;
        if (this.aggregations == null) {
            aggregations = null;
        } else {
            aggregations = aggregations().build();
        }
        final Query query;
        if (queryBuilder != null) {
            query = queryBuilder.build();
        } else {
            query = null;
        }

        return new Search(
            from, size, query, sorts, aggregations, source
        );
    }

    private ImmutableList.Builder<Sort> sort() {
        if (sort == null) {
            sort = ImmutableList.builder();
        }
        return sort;
    }

    private ImmutableMap.Builder<String, Aggregation> aggregations() {
        if (aggregations == null) {
            aggregations = ImmutableMap.builder();
        }
        return aggregations;
    }
}
