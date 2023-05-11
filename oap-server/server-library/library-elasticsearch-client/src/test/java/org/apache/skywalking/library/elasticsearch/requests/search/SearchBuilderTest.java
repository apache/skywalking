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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SearchBuilderTest {
    @Test
    public void searchQueryShouldBeUpdatableAfterSet() {
        final BoolQueryBuilder queryBuilder = Query.bool();
        final SearchBuilder searchBuilder = Search.builder().query(queryBuilder);
        queryBuilder.must(Query.term("t", "v"));
        queryBuilder.should(Query.term("t", "v"));
        queryBuilder.mustNot(Query.term("t2", "v2"));
        queryBuilder.shouldNot(Query.term("t2", "v2"));
        queryBuilder.shouldNot(Query.term("t2", "v2"));

        final BoolQuery query = (BoolQuery) searchBuilder.build().getQuery();
        assertThat(query.getMust()).hasSize(1);
        assertThat(query.getShould()).hasSize(1);
        assertThat(query.getMustNot()).hasSize(1);
        assertThat(query.getShouldNot()).hasSize(2);
    }

    @Test
    public void searchQueryBuilderShouldNotBeSetMultipleTimes() {
        assertThrows(IllegalStateException.class, () -> {
            final BoolQueryBuilder queryBuilder = Query.bool();
            final SearchBuilder searchBuilder = Search.builder().query(queryBuilder);
            searchBuilder.query(Query.bool());
        });
    }

    @Test
    public void searchQueryShouldNotBeSetMultipleTimes() {
        assertThrows(IllegalStateException.class, () -> {
            final SearchBuilder searchBuilder = Search.builder().query(Query.bool().build());
            searchBuilder.query(Query.bool().build());
        });
    }

    @Test
    public void searchQueryAndBuilderShouldNotBeSetSimultaneously() {
        assertThrows(IllegalStateException.class, () -> {
            final SearchBuilder searchBuilder = Search.builder().query(Query.bool().build());
            searchBuilder.query(Query.bool());
        });
    }
}
