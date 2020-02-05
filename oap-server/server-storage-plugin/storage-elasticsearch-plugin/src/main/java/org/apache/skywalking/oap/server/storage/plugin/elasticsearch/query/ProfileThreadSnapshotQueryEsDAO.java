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

import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
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

public class ProfileThreadSnapshotQueryEsDAO extends EsDAO implements IProfileThreadSnapshotQueryDAO {

    private final int querySegemntMaxSize;

    public ProfileThreadSnapshotQueryEsDAO(ElasticSearchClient client, int profileTaskQueryMaxSize) {
        super(client);
        this.querySegemntMaxSize = profileTaskQueryMaxSize;
    }

    @Override
    public List<String> getProfiledSegmentList(String taskId) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);

        boolQueryBuilder.must().add(QueryBuilders.termQuery(ProfileThreadSnapshotRecord.TASK_ID, taskId));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(ProfileThreadSnapshotRecord.SEQUENCE, 0));

        sourceBuilder.size(querySegemntMaxSize);
        sourceBuilder.sort(ProfileThreadSnapshotRecord.DUMP_TIME, SortOrder.DESC);

        final SearchResponse response = getClient().search(ProfileThreadSnapshotRecord.INDEX_NAME, sourceBuilder);

        final LinkedList<String> segments = new LinkedList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            segments.add((String) searchHit.getSourceAsMap().get(ProfileThreadSnapshotRecord.SEGMENT_ID));
        }
        return segments;
    }
}
