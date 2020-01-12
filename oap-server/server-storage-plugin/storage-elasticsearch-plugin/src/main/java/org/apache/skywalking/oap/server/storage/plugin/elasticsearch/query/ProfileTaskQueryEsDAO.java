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
import org.apache.skywalking.oap.server.core.profile.ProfileTaskNoneStream;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * @author MrPro
 */
public class ProfileTaskQueryEsDAO extends EsDAO implements IProfileTaskQueryDAO {

    private final int queryMaxSize;

    public ProfileTaskQueryEsDAO(ElasticSearchClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override
    public List<ProfileTask> getTaskList(Integer serviceId, String endpointName, Long startTimeBucket, Long endTimeBucket, Integer limit) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);

        if (serviceId != null) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(ProfileTaskNoneStream.SERVICE_ID, serviceId));
        }

        if (StringUtil.isNotEmpty(endpointName)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(ProfileTaskNoneStream.ENDPOINT_NAME, endpointName));
        }

        if (startTimeBucket != null) {
            boolQueryBuilder.must().add(QueryBuilders.rangeQuery(ProfileTaskNoneStream.TIME_BUCKET).gte(startTimeBucket));
        }

        if (endTimeBucket != null) {
            boolQueryBuilder.must().add(QueryBuilders.rangeQuery(ProfileTaskNoneStream.TIME_BUCKET).lte(endTimeBucket));
        }

        if (limit != null) {
            sourceBuilder.size(limit);
        } else {
            sourceBuilder.size(queryMaxSize);
        }

        sourceBuilder.sort(ProfileTaskNoneStream.START_TIME, SortOrder.DESC);

        final SearchResponse response = getClient().search(ProfileTaskNoneStream.INDEX_NAME, sourceBuilder);

        final LinkedList<ProfileTask> tasks = new LinkedList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            tasks.add(parseTask(searchHit));
        }

        return tasks;
    }

    @Override
    public ProfileTask getById(String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }

        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.idsQuery().addIds(id));
        sourceBuilder.size(1);

        final SearchResponse response = getClient().search(ProfileTaskNoneStream.INDEX_NAME, sourceBuilder);

        if (response.getHits().getHits().length > 0) {
            return parseTask(response.getHits().getHits()[0]);
        }

        return null;
    }

    private ProfileTask parseTask(SearchHit data) {
        return ProfileTask.builder()
                .id(data.getId())
                .serviceId(((Number) data.getSourceAsMap().get(ProfileTaskNoneStream.SERVICE_ID)).intValue())
                .endpointName((String) data.getSourceAsMap().get(ProfileTaskNoneStream.ENDPOINT_NAME))
                .startTime(((Number) data.getSourceAsMap().get(ProfileTaskNoneStream.START_TIME)).longValue())
                .createTime(((Number) data.getSourceAsMap().get(ProfileTaskNoneStream.CREATE_TIME)).longValue())
                .duration(((Number) data.getSourceAsMap().get(ProfileTaskNoneStream.DURATION)).intValue())
                .minDurationThreshold(((Number) data.getSourceAsMap().get(ProfileTaskNoneStream.MIN_DURATION_THRESHOLD)).intValue())
                .dumpPeriod(((Number) data.getSourceAsMap().get(ProfileTaskNoneStream.DUMP_PERIOD)).intValue())
                .maxSamplingCount(((Number) data.getSourceAsMap().get(ProfileTaskNoneStream.MAX_SAMPLING_COUNT)).intValue()).build();
    }
}
