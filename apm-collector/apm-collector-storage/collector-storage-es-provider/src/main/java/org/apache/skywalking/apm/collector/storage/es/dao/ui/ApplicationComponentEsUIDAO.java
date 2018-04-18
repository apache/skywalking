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

package org.apache.skywalking.apm.collector.storage.es.dao.ui;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationComponentEsUIDAO extends EsDAO implements IApplicationComponentUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationComponentEsUIDAO.class);

    public ApplicationComponentEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public List<ApplicationComponent> load(Step step, long startTimeBucket, long endTimeBucket) {
        logger.debug("application component load, start time: {}, end time: {}", startTimeBucket, endTimeBucket);
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationComponentTable.TABLE);
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ApplicationComponentTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(ApplicationComponentTable.TIME_BUCKET.getName()).gte(startTimeBucket).lte(endTimeBucket));
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(AggregationBuilders.terms(ApplicationComponentTable.COMPONENT_ID.getName()).field(ApplicationComponentTable.COMPONENT_ID.getName()).size(100)
            .subAggregation(AggregationBuilders.terms(ApplicationComponentTable.APPLICATION_ID.getName()).field(ApplicationComponentTable.APPLICATION_ID.getName()).size(100)));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        Terms componentIdTerms = searchResponse.getAggregations().get(ApplicationComponentTable.COMPONENT_ID.getName());

        List<ApplicationComponent> applicationComponents = new LinkedList<>();
        for (Terms.Bucket componentIdBucket : componentIdTerms.getBuckets()) {
            int componentId = componentIdBucket.getKeyAsNumber().intValue();
            buildApplicationComponents(componentIdBucket, componentId, applicationComponents);
        }

        return applicationComponents;
    }

    private void buildApplicationComponents(Terms.Bucket componentBucket, int componentId,
        List<ApplicationComponent> applicationComponents) {
        Terms peerIdTerms = componentBucket.getAggregations().get(ApplicationComponentTable.APPLICATION_ID.getName());
        for (Terms.Bucket peerIdBucket : peerIdTerms.getBuckets()) {
            int applicationId = peerIdBucket.getKeyAsNumber().intValue();

            ApplicationComponent applicationComponent = new ApplicationComponent();
            applicationComponent.setComponentId(componentId);
            applicationComponent.setApplicationId(applicationId);
            applicationComponents.add(applicationComponent);
        }
    }
}
