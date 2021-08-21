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

import static java.util.Objects.requireNonNull;

public final class BoolQueryBuilder implements QueryBuilder {
    private ImmutableList.Builder<Query> must;
    private ImmutableList.Builder<Query> mustNot;
    private ImmutableList.Builder<Query> should;
    private ImmutableList.Builder<Query> shouldNot;

    BoolQueryBuilder() {
    }

    public BoolQueryBuilder must(Query query) {
        requireNonNull(query, "query");
        must().add(query);
        return this;
    }

    public BoolQueryBuilder must(QueryBuilder queryBuilder) {
        requireNonNull(queryBuilder, "queryBuilder");
        return must(queryBuilder.build());
    }

    public BoolQueryBuilder mustNot(Query query) {
        requireNonNull(query, "query");
        mustNot().add(query);
        return this;
    }

    public BoolQueryBuilder mustNot(QueryBuilder queryBuilder) {
        requireNonNull(queryBuilder, "queryBuilder");
        return mustNot(queryBuilder.build());
    }

    public BoolQueryBuilder should(Query query) {
        requireNonNull(query, "query");
        should().add(query);
        return this;
    }

    public BoolQueryBuilder should(QueryBuilder queryBuilder) {
        requireNonNull(queryBuilder, "queryBuilder");
        return should(queryBuilder.build());
    }

    public BoolQueryBuilder shouldNot(Query query) {
        requireNonNull(query, "query");
        shouldNot().add(query);
        return this;
    }

    public BoolQueryBuilder shouldNot(QueryBuilder queryBuilder) {
        requireNonNull(queryBuilder, "queryBuilder");
        return shouldNot(queryBuilder.build());
    }

    private ImmutableList.Builder<Query> must() {
        if (must == null) {
            must = ImmutableList.builder();
        }
        return must;
    }

    private ImmutableList.Builder<Query> mustNot() {
        if (mustNot == null) {
            mustNot = ImmutableList.builder();
        }
        return mustNot;
    }

    private ImmutableList.Builder<Query> should() {
        if (should == null) {
            should = ImmutableList.builder();
        }
        return should;
    }

    private ImmutableList.Builder<Query> shouldNot() {
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
            must = this.must.build();
        }
        final ImmutableList<Query> should;
        if (this.should == null) {
            should = null;
        } else {
            should = this.should.build();
        }
        final ImmutableList<Query> mustNot;
        if (this.mustNot == null) {
            mustNot = null;
        } else {
            mustNot = this.mustNot.build();
        }
        final ImmutableList<Query> shouldNot;
        if (this.shouldNot == null) {
            shouldNot = null;
        } else {
            shouldNot = this.shouldNot.build();
        }
        return new BoolQuery(must, mustNot, should, shouldNot);
    }
}
