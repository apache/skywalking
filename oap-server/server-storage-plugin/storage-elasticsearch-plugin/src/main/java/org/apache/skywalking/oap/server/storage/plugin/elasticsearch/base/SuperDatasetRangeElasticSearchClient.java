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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import static org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeSeriesUtils.querySuperDatasetIndices;

public class SuperDatasetRangeElasticSearchClient {

    public static SearchResponse search(ElasticSearchClient client,
                                        String rangeTimeName,
                                        String indexName,
                                        SearchSourceBuilder searchSourceBuilder) throws IOException {
        Optional<RangeQueryBuilder> timeRangeQueryBuilder = findTimeRangeQueryBuilder(searchSourceBuilder, rangeTimeName);
        String[] indexNames = timeRangeQueryBuilder.map(item -> querySuperDatasetIndices(indexName, (Long) item.from(), (Long) item.to()))
                                                   .orElseGet(() -> new String[] {indexName});
        return client.search(indexNames, searchSourceBuilder);
    }

    /**
     * query rangeQueryBuilder on different query ways
     *
     * @param searchSourceBuilder es query params
     * @param rangeTimeName       the range time name define in es query params
     * @return the time range query builder
     */
    private static Optional<RangeQueryBuilder> findTimeRangeQueryBuilder(final SearchSourceBuilder searchSourceBuilder,
                                                                         final String rangeTimeName) {
        QueryBuilder query = searchSourceBuilder.query();
        if (query instanceof RangeQueryBuilder) {
            RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) query;
            return rangeQueryBuilder.fieldName().equals(rangeTimeName) ? Optional.of(rangeQueryBuilder) : Optional.empty();
        } else if (query instanceof BoolQueryBuilder) {
            return findTimeRangeQueryBuilder(rangeTimeName, ((BoolQueryBuilder) query).must(), ((BoolQueryBuilder) query).filter());
        } else {
            return Optional.empty();
        }
    }

    @SafeVarargs
    private static Optional<RangeQueryBuilder> findTimeRangeQueryBuilder(final String rangeTimeName,
                                                                         final List<QueryBuilder>... queryBuildersArray) {
        for (final List<QueryBuilder> queryBuilders : queryBuildersArray) {
            Optional<RangeQueryBuilder> rangeQueryBuilder = queryBuilders
                .stream()
                .filter(item -> rangeTimeName.equals(item.getName()))
                .filter(item -> item instanceof RangeQueryBuilder)
                .map(item -> (RangeQueryBuilder) item)
                .findFirst();
            if (rangeQueryBuilder.isPresent()) {
                return rangeQueryBuilder;
            }
        }
        return Optional.empty();
    }
}
