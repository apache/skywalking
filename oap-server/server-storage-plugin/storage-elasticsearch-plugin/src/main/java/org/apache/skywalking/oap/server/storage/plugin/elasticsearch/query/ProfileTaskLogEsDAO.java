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
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

public class ProfileTaskLogEsDAO extends EsDAO implements IProfileTaskLogQueryDAO {
    private final int queryMaxSize;

    public ProfileTaskLogEsDAO(ElasticSearchClient client, int profileTaskQueryMaxSize) {
        super(client);
        // query log size use profile task query max size * per log count
        this.queryMaxSize = profileTaskQueryMaxSize * 50;
    }

    @Override
    public List<ProfileTaskLog> getTaskLogList() throws IOException {
        final SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);

        sourceBuilder.sort(ProfileTaskLogRecord.OPERATION_TIME, SortOrder.DESC);
        sourceBuilder.size(queryMaxSize);

        final SearchResponse response = getClient().search(
            IndexController.LogicIndicesRegister.getPhysicalTableName(ProfileTaskLogRecord.INDEX_NAME), sourceBuilder);

        final LinkedList<ProfileTaskLog> tasks = new LinkedList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            tasks.add(parseTaskLog(searchHit));
        }

        return tasks;
    }

    private ProfileTaskLog parseTaskLog(SearchHit data) {
        return ProfileTaskLog.builder()
                             .id(data.getId())
                             .taskId((String) data.getSourceAsMap().get(ProfileTaskLogRecord.TASK_ID))
                             .instanceId((String) data.getSourceAsMap().get(ProfileTaskLogRecord.INSTANCE_ID))
                             .operationType(ProfileTaskLogOperationType.parse(
                                 ((Number) data.getSourceAsMap().get(ProfileTaskLogRecord.OPERATION_TYPE)).intValue()))
                             .operationTime(
                                 ((Number) data.getSourceAsMap().get(ProfileTaskLogRecord.OPERATION_TIME)).longValue())
                             .build();
    }
}
