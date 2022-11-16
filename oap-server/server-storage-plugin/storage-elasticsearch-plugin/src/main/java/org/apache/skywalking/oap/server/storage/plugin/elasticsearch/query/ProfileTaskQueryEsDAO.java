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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

public class ProfileTaskQueryEsDAO extends EsDAO implements IProfileTaskQueryDAO {

    private final int queryMaxSize;

    public ProfileTaskQueryEsDAO(ElasticSearchClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override
    public List<ProfileTask> getTaskList(String serviceId, String endpointName,
                                         Long startTimeBucket, Long endTimeBucket,
                                         Integer limit) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ProfileTaskRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(ProfileTaskRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, ProfileTaskRecord.INDEX_NAME));
        }

        if (StringUtil.isNotEmpty(serviceId)) {
            query.must(Query.term(ProfileTaskRecord.SERVICE_ID, serviceId));
        }

        if (StringUtil.isNotEmpty(endpointName)) {
            query.must(Query.term(ProfileTaskRecord.ENDPOINT_NAME, endpointName));
        }

        if (startTimeBucket != null) {
            query.must(Query.range(ProfileTaskRecord.TIME_BUCKET).gte(startTimeBucket));
        }

        if (endTimeBucket != null) {
            query.must(Query.range(ProfileTaskRecord.TIME_BUCKET).lte(endTimeBucket));
        }

        final SearchBuilder search = Search.builder().query(query);

        if (limit != null) {
            search.size(limit);
        } else {
            search.size(queryMaxSize);
        }

        search.sort(ProfileTaskRecord.START_TIME, Sort.Order.DESC);

        final SearchResponse response = getClient().search(index, search.build());

        final LinkedList<ProfileTask> tasks = new LinkedList<>();
        for (SearchHit searchHit : response.getHits()) {
            tasks.add(parseTask(searchHit));
        }

        return tasks;
    }

    @Override
    public ProfileTask getById(String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ProfileTaskRecord.INDEX_NAME);

        final SearchBuilder search = Search.builder()
                                           .query(Query.bool().must(Query.term(ProfileTaskRecord.TASK_ID, id)))
                                           .size(1);

        final SearchResponse response = getClient().search(index, search.build());

        if (response.getHits().getHits().size() > 0) {
            return parseTask(response.getHits().getHits().iterator().next());
        }

        return null;
    }

    private ProfileTask parseTask(SearchHit data) {
        final Map<String, Object> source = data.getSource();
        return ProfileTask.builder()
                          .id((String) source.get(ProfileTaskRecord.TASK_ID))
                          .serviceId((String) source.get(ProfileTaskRecord.SERVICE_ID))
                          .endpointName((String) source.get(ProfileTaskRecord.ENDPOINT_NAME))
                          .startTime(
                              ((Number) source.get(ProfileTaskRecord.START_TIME)).longValue())
                          .createTime(
                              ((Number) source.get(ProfileTaskRecord.CREATE_TIME)).longValue())
                          .duration(((Number) source.get(ProfileTaskRecord.DURATION)).intValue())
                          .minDurationThreshold(((Number) source.get(
                              ProfileTaskRecord.MIN_DURATION_THRESHOLD)).intValue())
                          .dumpPeriod(
                              ((Number) source.get(ProfileTaskRecord.DUMP_PERIOD)).intValue())
                          .maxSamplingCount(
                              ((Number) source.get(ProfileTaskRecord.MAX_SAMPLING_COUNT))
                                  .intValue())
                          .build();
    }
}
