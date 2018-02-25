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
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMappingTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
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
public class ApplicationMappingEsUIDAO extends EsHttpDAO implements IApplicationMappingUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationMappingEsUIDAO.class);

    public ApplicationMappingEsUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public List<ApplicationMapping> load(Step step, long startTime, long endTime) {
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationMappingTable.TABLE);

//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
//        searchRequestBuilder.setTypes(ApplicationMappingTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
//        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(ApplicationMappingTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
//        searchRequestBuilder.setSize(0);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(ApplicationMappingTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        searchSourceBuilder.size(0);
        searchSourceBuilder.aggregation(
            AggregationBuilders.terms(ApplicationMappingTable.COLUMN_APPLICATION_ID).field(ApplicationMappingTable.COLUMN_APPLICATION_ID).size(100)
                .subAggregation(AggregationBuilders.terms(ApplicationMappingTable.COLUMN_MAPPING_APPLICATION_ID).field(ApplicationMappingTable.COLUMN_MAPPING_APPLICATION_ID).size(100)));
        
//        searchRequestBuilder.addAggregation(
//            AggregationBuilders.terms(ApplicationMappingTable.COLUMN_APPLICATION_ID).field(ApplicationMappingTable.COLUMN_APPLICATION_ID).size(100)
//                .subAggregation(AggregationBuilders.terms(ApplicationMappingTable.COLUMN_MAPPING_APPLICATION_ID).field(ApplicationMappingTable.COLUMN_MAPPING_APPLICATION_ID).size(100)));
       
        
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(tableName).build();
        
        SearchResult result =  getClient().execute(search);
        
//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

//        Terms applicationIdTerms = searchResponse.getAggregations().get(ApplicationMappingTable.COLUMN_APPLICATION_ID);
        
        TermsAggregation applicationIdTerms=  result.getAggregations().getTermsAggregation(ApplicationMappingTable.COLUMN_APPLICATION_ID);

        List<ApplicationMapping> applicationMappings = new LinkedList<>();
        for (Entry applicationIdBucket : applicationIdTerms.getBuckets()) {
            int applicationId =  Integer.parseInt(applicationIdBucket.getKeyAsString());
            TermsAggregation addressIdTerms = applicationIdBucket.getTermsAggregation(ApplicationMappingTable.COLUMN_MAPPING_APPLICATION_ID);
            for (Entry addressIdBucket : addressIdTerms.getBuckets()) {
                int addressId = Integer.parseInt(addressIdBucket.getKeyAsString()) ;

                ApplicationMapping applicationMapping = new ApplicationMapping();
                applicationMapping.setApplicationId(applicationId);
                applicationMapping.setMappingApplicationId(addressId);
                applicationMappings.add(applicationMapping);
            }
        }
        logger.debug("application mapping data: {}", applicationMappings.toString());
        return applicationMappings;
    }
}
