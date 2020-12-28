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

import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TopNRecordsQueryEsDAO extends EsDAO implements ITopNRecordsQueryDAO {
    public TopNRecordsQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<SelectedRecord> readSampledRecords(final TopNCondition condition,
                                                   final String valueColumnName,
                                                   final Duration duration) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(TopN.TIME_BUCKET)
                                                 .gte(duration.getStartTimeBucketInSec())
                                                 .lte(duration.getEndTimeBucketInSec()));

        if (StringUtil.isNotEmpty(condition.getParentService())) {
            final String serviceId = IDManager.ServiceID.buildId(condition.getParentService(), condition.isNormal());
            boolQueryBuilder.must().add(QueryBuilders.termQuery(TopN.SERVICE_ID, serviceId));
        }

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(condition.getTopN())
                     .sort(valueColumnName, condition.getOrder().equals(Order.DES) ? SortOrder.DESC : SortOrder.ASC);
        SearchResponse response = getClient().search(condition.getName(), sourceBuilder);

        List<SelectedRecord> results = new ArrayList<>(condition.getTopN());

        for (SearchHit searchHit : response.getHits().getHits()) {
            SelectedRecord record = new SelectedRecord();
            final Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();
            record.setName((String) sourceAsMap.get(TopN.STATEMENT));
            record.setRefId((String) sourceAsMap.get(TopN.TRACE_ID));
            record.setId(record.getRefId());
            record.setValue(((Number) sourceAsMap.get(valueColumnName)).toString());
            results.add(record);
        }

        return results;
    }

    @Override
    public List<SelectedRecord> readSampledRecordsMetric(TopNCondition condition, String valueCName, Duration duration) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        final RangeQueryBuilder queryBuilder = QueryBuilders.rangeQuery(TopN.TIME_BUCKET)
                .lte(duration.getEndTimeBucketInSec())
                .gte(duration.getStartTimeBucketInSec());
        boolQueryBuilder.must().add(queryBuilder);

        boolean asc = false;
        if (condition.getOrder().equals(Order.ASC)) {
            asc = true;
        }

        if (StringUtil.isNotEmpty(condition.getParentService())) {
            final String serviceId = IDManager.ServiceID.buildId(condition.getParentService(), condition.isNormal());
            boolQueryBuilder.must().add(QueryBuilders.termQuery(TopN.SERVICE_ID, serviceId));
        }

        sourceBuilder.query(boolQueryBuilder);

        sourceBuilder.aggregation(
                AggregationBuilders.terms(TopN.STATEMENT)
                        .field(TopN.STATEMENT)
                        .order(BucketOrder.count(asc)))
                .size(condition.getTopN());

        SearchResponse response = getClient().search(condition.getName(), sourceBuilder);

        List<SelectedRecord> topNList = new ArrayList<>();
        Terms idTerms = response.getAggregations().get(TopN.STATEMENT);
        for (Terms.Bucket termsBucket : idTerms.getBuckets()) {
            SelectedRecord record = new SelectedRecord();
            record.setName(condition.getParentService());
            record.setId(termsBucket.getKeyAsString());
            long value = termsBucket.getDocCount();
            record.setValue(String.valueOf(value));
            topNList.add(record);
        }

        return topNList;
    }

}
