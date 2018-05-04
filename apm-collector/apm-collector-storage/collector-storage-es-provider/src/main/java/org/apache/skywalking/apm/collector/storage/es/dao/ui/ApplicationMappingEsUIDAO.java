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
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMappingTable;
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
public class ApplicationMappingEsUIDAO extends EsDAO implements IApplicationMappingUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationMappingEsUIDAO.class);

    public ApplicationMappingEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public List<ApplicationMapping> load(Step step, long startTimeBucket, long endTimeBucket) {
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationMappingTable.TABLE);

        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ApplicationMappingTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(ApplicationMappingTable.TIME_BUCKET.getName()).gte(startTimeBucket).lte(endTimeBucket));
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(
            AggregationBuilders.terms(ApplicationMappingTable.APPLICATION_ID.getName()).field(ApplicationMappingTable.APPLICATION_ID.getName()).size(100)
                .subAggregation(AggregationBuilders.terms(ApplicationMappingTable.MAPPING_APPLICATION_ID.getName()).field(ApplicationMappingTable.MAPPING_APPLICATION_ID.getName()).size(100)));
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        Terms applicationIdTerms = searchResponse.getAggregations().get(ApplicationMappingTable.APPLICATION_ID.getName());

        List<ApplicationMapping> applicationMappings = new LinkedList<>();
        for (Terms.Bucket applicationIdBucket : applicationIdTerms.getBuckets()) {
            int applicationId = applicationIdBucket.getKeyAsNumber().intValue();
            Terms mappingApplicationIdTerms = applicationIdBucket.getAggregations().get(ApplicationMappingTable.MAPPING_APPLICATION_ID.getName());
            for (Terms.Bucket mappingApplicationIdBucket : mappingApplicationIdTerms.getBuckets()) {
                int mappingApplicationId = mappingApplicationIdBucket.getKeyAsNumber().intValue();

                ApplicationMapping applicationMapping = new ApplicationMapping();
                applicationMapping.setApplicationId(applicationId);
                applicationMapping.setMappingApplicationId(mappingApplicationId);
                applicationMappings.add(applicationMapping);
            }
        }
        logger.debug("application mapping data: {}", applicationMappings.toString());
        return applicationMappings;
    }
}
