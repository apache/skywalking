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

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * @author MrPro
 */
public class ProfileTaskQueryEs7DAO extends EsDAO implements IProfileTaskQueryDAO {
    public ProfileTaskQueryEs7DAO(ElasticSearchClient client) {
        super(client);
    }


    @Override
    public List<ProfileTask> getTaskList(Integer serviceId, String endpointName, long startTimeBucket, long endTimeBucket) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);

        if (serviceId != null) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(ProfileTaskNoneStream.SERVICE_ID, serviceId));
        }

        if (StringUtil.isNotEmpty(endpointName)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(ProfileTaskNoneStream.ENDPOINT_NAME, endpointName));
        }

        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(ProfileTaskNoneStream.TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));

        final SearchResponse response = getClient().search(ProfileTaskNoneStream.INDEX_NAME, sourceBuilder);

        final LinkedList<ProfileTask> tasks = new LinkedList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            tasks.add(parseTask(searchHit));
        }

        return tasks;
    }

    private ProfileTask parseTask(SearchHit data) {
        return ProfileTask.builder()
                .id(data.getId())
                .serviceId(((Number) data.getSourceAsMap().get(ProfileTaskNoneStream.SERVICE_ID)).intValue())
                .endpointName((String) data.getSourceAsMap().get(ProfileTaskNoneStream.ENDPOINT_NAME))
                .startTime(((Number) data.getSourceAsMap().get(ProfileTaskNoneStream.START_TIME)).longValue())
                .duration(((Number) data.getSourceAsMap().get(ProfileTaskNoneStream.DURATION)).intValue())
                .minDurationThreshold(((Number) data.getSourceAsMap().get(ProfileTaskNoneStream.MIN_DURATION_THRESHOLD)).intValue())
                .dumpPeriod(((Number) data.getSourceAsMap().get(ProfileTaskNoneStream.DUMP_PERIOD)).intValue()).build();
    }
}
