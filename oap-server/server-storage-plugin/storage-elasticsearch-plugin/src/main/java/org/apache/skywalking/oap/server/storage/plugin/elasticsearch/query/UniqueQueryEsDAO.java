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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.io.IOException;
import java.util.*;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.apache.skywalking.oap.server.core.query.sql.Where;
import org.apache.skywalking.oap.server.core.storage.TimePyramidTableNameBuilder;
import org.apache.skywalking.oap.server.core.storage.query.*;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * @author peng-yongsheng
 */
public class UniqueQueryEsDAO extends EsDAO implements IUniqueQueryDAO {

    public UniqueQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public List<TwoIdGroup> aggregation(String indName, Step step, long startTB, long endTB, Where where,
        String idCName1, String idCName2) throws IOException {
        String indexName = TimePyramidTableNameBuilder.build(step, indName);

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        queryBuild(sourceBuilder, where, startTB, endTB);

        sourceBuilder.aggregation(AggregationBuilders.terms(idCName1).field(idCName1).size(1000)
            .subAggregation(AggregationBuilders.terms(idCName2).field(idCName2).size(1000)));

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        List<TwoIdGroup> values = new ArrayList<>();
        Terms id1Terms = response.getAggregations().get(idCName1);
        for (Terms.Bucket id1Bucket : id1Terms.getBuckets()) {
            Terms id2Terms = id1Bucket.getAggregations().get(idCName2);
            for (Terms.Bucket id2Bucket : id2Terms.getBuckets()) {
                TwoIdGroup value = new TwoIdGroup();
                value.setId1(id1Bucket.getKeyAsNumber().intValue());
                value.setId2(id2Bucket.getKeyAsNumber().intValue());
                values.add(value);
            }
        }
        return values;
    }
}
