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

import com.google.common.collect.ImmutableList;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public final class BoolQueryBuilder implements QueryBuilder {
    private ImmutableList.Builder<QueryBuilder> must;
    private ImmutableList.Builder<QueryBuilder> mustNot;
    private ImmutableList.Builder<QueryBuilder> should;
    private ImmutableList.Builder<QueryBuilder> shouldNot;

    BoolQueryBuilder() {
    }

    public BoolQueryBuilder must(QueryBuilder queryBuilder) {
        requireNonNull(queryBuilder, "queryBuilder");
        must().add(queryBuilder);
        return this;
    }

    public BoolQueryBuilder mustNot(QueryBuilder queryBuilder) {
        requireNonNull(queryBuilder, "queryBuilder");
        mustNot().add(queryBuilder);
        return this;
    }

    public BoolQueryBuilder should(QueryBuilder queryBuilder) {
        requireNonNull(queryBuilder, "queryBuilder");
        should().add(queryBuilder);
        return this;
    }

    public BoolQueryBuilder shouldNot(QueryBuilder queryBuilder) {
        requireNonNull(queryBuilder, "queryBuilder");
        shouldNot().add(queryBuilder);
        return this;
    }

    private ImmutableList.Builder<QueryBuilder> must() {
        if (must == null) {
            must = ImmutableList.builder();
        }
        return must;
    }

    private ImmutableList.Builder<QueryBuilder> mustNot() {
        if (mustNot == null) {
            mustNot = ImmutableList.builder();
        }
        return mustNot;
    }

    private ImmutableList.Builder<QueryBuilder> should() {
        if (should == null) {
            should = ImmutableList.builder();
        }
        return should;
    }

    private ImmutableList.Builder<QueryBuilder> shouldNot() {
        if (shouldNot == null) {
            shouldNot = ImmutableList.builder();
        }
        return shouldNot;
    }

    @Override
    public Query build() {
        final ImmutableList<Query> must;
        if (this.must == null) {
            must = null;
        } else {
            must = this.must.build().stream()
                            .map(QueryBuilder::build)
                            .collect(toImmutableList());
        }
        final ImmutableList<Query> should;
        if (this.should == null) {
            should = null;
        } else {
            should = this.should.build().stream()
                                .map(QueryBuilder::build)
                                .collect(toImmutableList());
        }
        final ImmutableList<Query> mustNot;
        if (this.mustNot == null) {
            mustNot = null;
        } else {
            mustNot = this.mustNot.build().stream()
                                  .map(QueryBuilder::build)
                                  .collect(toImmutableList());
        }
        final ImmutableList<Query> shouldNot;
        if (this.shouldNot == null) {
            shouldNot = null;
        } else {
            shouldNot = this.shouldNot.build().stream()
                                      .map(QueryBuilder::build)
                                      .collect(toImmutableList());
        }
        return new BoolQuery(must, mustNot, should, shouldNot);
    }
}
