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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.Aggregation;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.AggregationBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

public class ProfileThreadSnapshotQueryEsDAO extends EsDAO
    implements IProfileThreadSnapshotQueryDAO {

    private final int querySegmentMaxSize;

    protected final ProfileThreadSnapshotRecord.Builder builder =
        new ProfileThreadSnapshotRecord.Builder();

    public ProfileThreadSnapshotQueryEsDAO(ElasticSearchClient client,
                                           int profileTaskQueryMaxSize) {
        super(client);
        this.querySegmentMaxSize = profileTaskQueryMaxSize;
    }

    @Override
    public List<ProfileThreadSnapshotRecord> queryRecords(String taskId) throws IOException {
        final BoolQueryBuilder segmentIdQuery =
            Query.bool()
                .must(Query.term(ProfileThreadSnapshotRecord.TASK_ID, taskId))
                .must(Query.term(ProfileThreadSnapshotRecord.SEQUENCE, 0));
        if (IndexController.LogicIndicesRegister.isMergedTable(ProfileThreadSnapshotRecord.INDEX_NAME)) {
            segmentIdQuery.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, ProfileThreadSnapshotRecord.INDEX_NAME));
        }

        final SearchBuilder search =
            Search.builder().query(segmentIdQuery)
                .size(querySegmentMaxSize)
                .sort(
                    ProfileThreadSnapshotRecord.DUMP_TIME,
                    Sort.Order.DESC
                );

        SearchResponse response =
            getClient().search(
                IndexController.LogicIndicesRegister.getPhysicalTableName(
                    ProfileThreadSnapshotRecord.INDEX_NAME),
                search.build()
            );

        List<ProfileThreadSnapshotRecord> result = new ArrayList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            ProfileThreadSnapshotRecord record = builder.storage2Entity(
                new ElasticSearchConverter.ToEntity(ProfileThreadSnapshotRecord.INDEX_NAME, searchHit.getSource()));

            result.add(record);
        }
        return result;
    }

    @Override
    public int queryMinSequence(String segmentId, long start, long end) {
        return querySequenceWithAgg(
            Aggregation.min(ProfileThreadSnapshotRecord.SEQUENCE)
                       .field(ProfileThreadSnapshotRecord.SEQUENCE),
            segmentId, start, end
        );
    }

    @Override
    public int queryMaxSequence(String segmentId, long start, long end) {
        return querySequenceWithAgg(
            Aggregation.max(ProfileThreadSnapshotRecord.SEQUENCE)
                       .field(ProfileThreadSnapshotRecord.SEQUENCE),
            segmentId, start, end
        );
    }

    @Override
    public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId,
                                                          int minSequence,
                                                          int maxSequence) {
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(
            ProfileThreadSnapshotRecord.INDEX_NAME);

        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.term(ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId))
                 .must(Query.range(ProfileThreadSnapshotRecord.SEQUENCE)
                            .gte(minSequence)
                            .lt(maxSequence));

        final SearchBuilder search =
            Search.builder().query(query)
                  .size(maxSequence - minSequence);
        final SearchResponse response = getClient().search(index, search.build());

        List<ProfileThreadSnapshotRecord> result = new ArrayList<>(maxSequence - minSequence);
        for (SearchHit searchHit : response.getHits().getHits()) {
            ProfileThreadSnapshotRecord record = builder.storage2Entity(
                new ElasticSearchConverter.ToEntity(ProfileThreadSnapshotRecord.INDEX_NAME, searchHit.getSource()));

            result.add(record);
        }
        return result;
    }

    protected int querySequenceWithAgg(AggregationBuilder aggregationBuilder,
                                       String segmentId, long start, long end) {
        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.term(ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId))
                 .must(Query.range(ProfileThreadSnapshotRecord.DUMP_TIME).gte(start).lte(end));

        final SearchBuilder search =
            Search.builder()
                  .query(query).size(0)
                  .aggregation(aggregationBuilder);
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(
            ProfileThreadSnapshotRecord.INDEX_NAME);
        final SearchResponse response = getClient().search(index, search.build());
        final Map<String, Object> agg =
            (Map<String, Object>) response.getAggregations()
                                          .get(ProfileThreadSnapshotRecord.SEQUENCE);

        final Object val = agg.get("value");
        // return not found if no snapshot found
        if (val == null) {
            return -1;
        }
        return ((Number) val).intValue();
    }
}
