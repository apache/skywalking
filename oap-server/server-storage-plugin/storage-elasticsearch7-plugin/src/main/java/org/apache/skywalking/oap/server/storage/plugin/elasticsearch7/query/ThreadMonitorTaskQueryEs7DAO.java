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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query;

import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.profile.ThreadMonitorTaskNoneStream;
import org.apache.skywalking.oap.server.core.query.entity.ThreadMonitorTask;
import org.apache.skywalking.oap.server.core.storage.profile.IThreadMonitorTaskQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * @author MrPro
 */
public class ThreadMonitorTaskQueryEs7DAO extends EsDAO implements IThreadMonitorTaskQueryDAO {
    public ThreadMonitorTaskQueryEs7DAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<ThreadMonitorTask> getTaskListSearchOnStartTime(int serviceId, long taskStartTime, long taskEndTime) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);

        boolQueryBuilder.must().add(QueryBuilders.termQuery(ThreadMonitorTaskNoneStream.SERVICE_ID, serviceId));
        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(ThreadMonitorTaskNoneStream.MONITOR_START_TIME).gte(taskStartTime).lte(taskEndTime));

        final SearchResponse response = getClient().search(ThreadMonitorTaskNoneStream.INDEX_NAME, sourceBuilder);

        final LinkedList<ThreadMonitorTask> tasks = new LinkedList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            tasks.add(parseTask(searchHit));
        }

        return tasks;
    }

    @Override
    public List<ThreadMonitorTask> getTaskList(Integer serviceId, String endpointName, long startTimeBucket, long endTimeBucket) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);

        if (serviceId != null) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(ThreadMonitorTaskNoneStream.SERVICE_ID, serviceId));
        }

        if (!StringUtil.isBlank(endpointName)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(ThreadMonitorTaskNoneStream.ENDPOINT_NAME, endpointName));
        }

        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(ThreadMonitorTaskNoneStream.TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));

        final SearchResponse response = getClient().search(ThreadMonitorTaskNoneStream.INDEX_NAME, sourceBuilder);

        final LinkedList<ThreadMonitorTask> tasks = new LinkedList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            tasks.add(parseTask(searchHit));
        }

        return tasks;
    }

    private ThreadMonitorTask parseTask(SearchHit data) {
        return ThreadMonitorTask.builder()
                .id(data.getId())
                .serviceId(((Number) data.getSourceAsMap().get(ThreadMonitorTaskNoneStream.SERVICE_ID)).intValue())
                .endpointName((String) data.getSourceAsMap().get(ThreadMonitorTaskNoneStream.ENDPOINT_NAME))
                .startTime(((Number) data.getSourceAsMap().get(ThreadMonitorTaskNoneStream.MONITOR_START_TIME)).longValue())
                .duration(((Number) data.getSourceAsMap().get(ThreadMonitorTaskNoneStream.MONITOR_DURATION)).intValue())
                .minDurationThreshold(((Number) data.getSourceAsMap().get(ThreadMonitorTaskNoneStream.MIN_DURATION_THRESHOLD)).intValue())
                .dumpPeriod(((Number) data.getSourceAsMap().get(ThreadMonitorTaskNoneStream.DUMP_PERIOD)).intValue()).build();
    }
}
