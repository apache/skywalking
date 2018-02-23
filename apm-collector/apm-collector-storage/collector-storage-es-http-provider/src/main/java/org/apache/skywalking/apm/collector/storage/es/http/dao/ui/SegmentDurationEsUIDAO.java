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

import java.util.List;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentDurationUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDurationTable;
import org.apache.skywalking.apm.collector.storage.ui.trace.BasicTrace;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceBrief;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;

/**
 * @author peng-yongsheng
 */
public class SegmentDurationEsUIDAO extends EsDAO implements ISegmentDurationUIDAO {

    public SegmentDurationEsUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override
    public TraceBrief loadTop(long startTime, long endTime, long minDuration, long maxDuration, String operationName,
        int applicationId, String traceId, int limit, int from) {
//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(SegmentDurationTable.TABLE);
//        searchRequestBuilder.setTypes(SegmentDurationTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
//        searchRequestBuilder.setQuery(boolQueryBuilder);
        List<QueryBuilder> mustQueryList = boolQueryBuilder.must();

        mustQueryList.add(QueryBuilders.rangeQuery(SegmentDurationTable.COLUMN_TIME_BUCKET).gte(startTime).lte(endTime));
        if (minDuration != 0 || maxDuration != 0) {
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(SegmentDurationTable.COLUMN_DURATION);
            if (minDuration != 0) {
                rangeQueryBuilder.gte(minDuration);
            }
            if (maxDuration != 0) {
                rangeQueryBuilder.lte(maxDuration);
            }
            boolQueryBuilder.must().add(rangeQueryBuilder);
        }
        if (StringUtils.isNotEmpty(operationName)) {
            mustQueryList.add(QueryBuilders.matchQuery(SegmentDurationTable.COLUMN_SERVICE_NAME, operationName));
        }
        if (StringUtils.isNotEmpty(traceId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SegmentDurationTable.COLUMN_SEGMENT_ID, traceId));
        }
        if (applicationId != 0) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(SegmentDurationTable.COLUMN_APPLICATION_ID, applicationId));
        }

//        searchRequestBuilder.setSize(limit);
//        searchRequestBuilder.setFrom(from);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);
        searchSourceBuilder.size(limit);
        searchSourceBuilder.from(from);

        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(SegmentDurationTable.TABLE).build();
        
//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        
         SearchResult result= getClient().execute(search); 

        TraceBrief traceBrief = new TraceBrief();
        traceBrief.setTotal(result.getTotal().intValue());
        
        JsonArray resultArray =  result.getJsonObject().getAsJsonObject("hits").getAsJsonArray("hits");

        for (JsonElement searchHit : resultArray) {
            BasicTrace basicTrace = new BasicTrace();

            JsonObject source = searchHit.getAsJsonObject().getAsJsonObject("_source");
            
            basicTrace.setTraceId(source.get(SegmentDurationTable.COLUMN_TRACE_ID).getAsString());
            basicTrace.setStart((source.get(SegmentDurationTable.COLUMN_START_TIME)).getAsLong());
            basicTrace.setOperationName(source.get(SegmentDurationTable.COLUMN_SERVICE_NAME).getAsString());
            basicTrace.setDuration((source.get(SegmentDurationTable.COLUMN_DURATION)).getAsInt());
            basicTrace.setError(BooleanUtils.valueToBoolean(source.get(SegmentDurationTable.COLUMN_IS_ERROR).getAsInt()));
            traceBrief.getTraces().add(basicTrace);
        }

        return traceBrief;
    }
}
