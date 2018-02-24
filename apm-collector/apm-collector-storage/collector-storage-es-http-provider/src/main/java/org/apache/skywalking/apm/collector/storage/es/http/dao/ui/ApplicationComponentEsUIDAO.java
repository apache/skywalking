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

package org.apache.skywalking.apm.collector.storage.es.http.dao.ui;

import java.util.LinkedList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.search.aggregation.TermsAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation.Entry;

/**
 * @author peng-yongsheng
 */
public class ApplicationComponentEsUIDAO extends EsHttpDAO implements IApplicationComponentUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationComponentEsUIDAO.class);

    public ApplicationComponentEsUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public List<ApplicationComponent> load(Step step, long startTime, long endTime) {
        logger.debug("application component load, start time: {}, end time: {}", startTime, endTime);
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationComponentTable.TABLE);
//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
//        searchRequestBuilder.setTypes(ApplicationComponentTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
//        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(ApplicationComponentTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
//        searchRequestBuilder.setSize(0);

//        searchRequestBuilder.addAggregation(AggregationBuilders.terms(ApplicationComponentTable.COLUMN_COMPONENT_ID).field(ApplicationComponentTable.COLUMN_COMPONENT_ID).size(100)
//            .subAggregation(AggregationBuilders.terms(ApplicationComponentTable.COLUMN_APPLICATION_ID).field(ApplicationComponentTable.COLUMN_APPLICATION_ID).size(100)));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(ApplicationComponentTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        searchSourceBuilder.size(0);
        searchSourceBuilder.aggregation(AggregationBuilders.terms(ApplicationComponentTable.COLUMN_COMPONENT_ID).field(ApplicationComponentTable.COLUMN_COMPONENT_ID).size(100)
            .subAggregation(AggregationBuilders.terms(ApplicationComponentTable.COLUMN_APPLICATION_ID).field(ApplicationComponentTable.COLUMN_APPLICATION_ID).size(100)));
        
        Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex(tableName)
                .build();
        
//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        
        SearchResult result = getClient().execute(search);
        
        TermsAggregation  componentIdTerms =  result.getAggregations().getTermsAggregation(ApplicationComponentTable.COLUMN_COMPONENT_ID);

//        Terms componentIdTerms = searchResponse.getAggregations().get(ApplicationComponentTable.COLUMN_COMPONENT_ID);

        List<ApplicationComponent> applicationComponents = new LinkedList<>();
        for (Entry componentIdBucket : componentIdTerms.getBuckets()) {
            int componentId =Integer.valueOf( componentIdBucket.getKeyAsString() );
            buildApplicationComponents(componentIdBucket, componentId, applicationComponents);
        }

        return applicationComponents;
    }

    private void buildApplicationComponents(Entry componentBucket, int componentId,
        List<ApplicationComponent> applicationComponents) {
        TermsAggregation peerIdTerms = componentBucket.getTermsAggregation(ApplicationComponentTable.COLUMN_APPLICATION_ID);
        for (Entry peerIdBucket : peerIdTerms.getBuckets()) {
            int applicationId = Integer.valueOf( peerIdBucket.getKeyAsString() );

            ApplicationComponent applicationComponent = new ApplicationComponent();
            applicationComponent.setComponentId(componentId);
            applicationComponent.setApplicationId(applicationId);
            applicationComponents.add(applicationComponent);
        }
    }
}
