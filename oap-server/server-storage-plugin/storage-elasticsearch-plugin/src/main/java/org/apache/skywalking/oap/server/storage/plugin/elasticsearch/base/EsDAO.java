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

import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.query.sql.Where;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * @author peng-yongsheng
 */
public abstract class EsDAO extends AbstractDAO<ElasticSearchClient> {

    public EsDAO(ElasticSearchClient client) {
        super(client);
    }

    public final void queryBuild(SearchSourceBuilder sourceBuilder, Where where, long startTB, long endTB) {
        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(Indicator.TIME_BUCKET).gte(startTB).lte(endTB);
        if (where.getKeyValues().isEmpty()) {
            sourceBuilder.query(rangeQueryBuilder);
        } else {
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            boolQuery.must().add(rangeQueryBuilder);

            where.getKeyValues().forEach(keyValues -> {
                if (keyValues.getValues().size() > 1) {
                    boolQuery.must().add(QueryBuilders.termsQuery(keyValues.getKey(), keyValues.getValues()));
                } else {
                    boolQuery.must().add(QueryBuilders.termQuery(keyValues.getKey(), keyValues.getValues().get(0)));
                }
            });
            sourceBuilder.query(boolQuery);
        }
        sourceBuilder.size(0);
    }
}
