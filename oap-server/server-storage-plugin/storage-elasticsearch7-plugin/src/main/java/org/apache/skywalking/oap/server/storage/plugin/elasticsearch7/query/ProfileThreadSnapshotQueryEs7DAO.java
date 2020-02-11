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

import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.ProfileThreadSnapshotQueryEsDAO;
import org.elasticsearch.search.aggregations.AggregationBuilders;

import java.io.IOException;

/**
 * Following aggregations APIs of ElasticsSearch 6 and 7 have different return type, need to re-compile based on different client jars.
 *
 * @see org.elasticsearch.search.aggregations.AggregationBuilders#min(java.lang.String)
 * @see org.elasticsearch.search.aggregations.AggregationBuilders#max(java.lang.String)
 */
public class ProfileThreadSnapshotQueryEs7DAO extends ProfileThreadSnapshotQueryEsDAO {
    public ProfileThreadSnapshotQueryEs7DAO(ElasticSearchClient client, int profileTaskQueryMaxSize) {
        super(client, profileTaskQueryMaxSize);
    }

    @Override
    public int queryMinSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg(AggregationBuilders.min(ProfileThreadSnapshotRecord.SEQUENCE).field(ProfileThreadSnapshotRecord.SEQUENCE), segmentId, start, end);
    }

    @Override
    public int queryMaxSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg(AggregationBuilders.max(ProfileThreadSnapshotRecord.SEQUENCE).field(ProfileThreadSnapshotRecord.SEQUENCE), segmentId, start, end);
    }
}
