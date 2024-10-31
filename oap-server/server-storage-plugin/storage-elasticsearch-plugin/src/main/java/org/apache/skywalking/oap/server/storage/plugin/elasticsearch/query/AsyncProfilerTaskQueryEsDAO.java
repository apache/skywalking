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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerEventType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AsyncProfilerTaskQueryEsDAO extends EsDAO implements IAsyncProfilerTaskQueryDAO {

    private static final Gson GSON = new Gson();

    private final int queryMaxSize;

    public AsyncProfilerTaskQueryEsDAO(ElasticSearchClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override
    public List<AsyncProfilerTask> getTaskList(String serviceId, Long startTimeBucket, Long endTimeBucket, Integer limit) {
        String index = IndexController.LogicIndicesRegister.getPhysicalTableName(AsyncProfilerTaskRecord.INDEX_NAME);
        BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(AsyncProfilerTaskRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, AsyncProfilerTaskRecord.INDEX_NAME));
        }

        if (StringUtil.isNotEmpty(serviceId)) {
            query.must(Query.term(AsyncProfilerTaskRecord.SERVICE_ID, serviceId));
        }

        if (startTimeBucket != null) {
            query.must(Query.range(AsyncProfilerTaskRecord.TIME_BUCKET).gte(startTimeBucket));
        }

        if (endTimeBucket != null) {
            query.must(Query.range(AsyncProfilerTaskRecord.TIME_BUCKET).lte(endTimeBucket));
        }
        SearchBuilder search = Search.builder().query(query);
        search.size(Objects.requireNonNullElse(limit, queryMaxSize));

        search.sort(AsyncProfilerTaskRecord.CREATE_TIME, Sort.Order.DESC);

        final SearchResponse response = getClient().search(index, search.build());

        final List<AsyncProfilerTask> tasks = new LinkedList<>();
        for (SearchHit searchHit : response.getHits()) {
            tasks.add(parseTask(searchHit));
        }

        return tasks;
    }

    @Override
    public AsyncProfilerTask getById(String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(AsyncProfilerTaskRecord.INDEX_NAME);

        final SearchBuilder search = Search.builder()
                .query(Query.bool().must(Query.term(AsyncProfilerTaskRecord.TASK_ID, id)))
                .size(1);

        final SearchResponse response = getClient().search(index, search.build());

        if (!response.getHits().getHits().isEmpty()) {
            return parseTask(response.getHits().getHits().iterator().next());
        }
        return null;
    }

    private AsyncProfilerTask parseTask(SearchHit data) {
        Map<String, Object> source = data.getSource();

        Type listType = new TypeToken<List<String>>() {
        }.getType();
        String events = (String) source.get(AsyncProfilerTaskRecord.EVENT_TYPES);
        List<String> eventList = GSON.fromJson(events, listType);
        String serviceInstanceIds = (String) source.get(AsyncProfilerTaskRecord.SERVICE_INSTANCE_IDS);
        List<String> instanceIdList = GSON.fromJson(serviceInstanceIds, listType);
        return AsyncProfilerTask.builder()
                .id((String) source.get(AsyncProfilerTaskRecord.TASK_ID))
                .serviceId((String) source.get(AsyncProfilerTaskRecord.SERVICE_ID))
                .serviceInstanceIds(instanceIdList)
                .createTime(((Number) source.get(AsyncProfilerTaskRecord.CREATE_TIME)).longValue())
                .duration(((Number) source.get(AsyncProfilerTaskRecord.DURATION)).intValue())
                .execArgs((String) source.get(AsyncProfilerTaskRecord.EXEC_ARGS))
                .events(AsyncProfilerEventType.valueOfList(eventList))
                .build();
    }
}
