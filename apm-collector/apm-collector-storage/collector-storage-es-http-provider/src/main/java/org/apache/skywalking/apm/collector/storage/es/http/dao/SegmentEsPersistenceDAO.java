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

package org.apache.skywalking.apm.collector.storage.es.http.dao;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.segment.Segment;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentTable;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.core.Index;
import io.searchbox.core.Update;

/**
 * @author cyberdak
 */
public class SegmentEsPersistenceDAO extends EsHttpDAO implements ISegmentPersistenceDAO<Index, Update, Segment> {

    private final Logger logger = LoggerFactory.getLogger(SegmentEsPersistenceDAO.class);

    public SegmentEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public Segment get(String id) {
        return null;
    }

    @Override public Update prepareBatchUpdate(Segment data) {
        return null;
    }

    @Override public Index prepareBatchInsert(Segment data) {
        Map<String, Object> source = new HashMap<>();
        source.put(SegmentTable.COLUMN_DATA_BINARY, new String(Base64.getEncoder().encode(data.getDataBinary())));
        source.put(SegmentTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        logger.debug("segment source: {}", source.toString());
        return new Index.Builder(source).index(SegmentTable.TABLE).type(SegmentTable.TABLE_TYPE).id(data.getId()).build();
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(SegmentTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));
        
        long deleted = getClient().batchDelete(SegmentTable.TABLE, searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, SegmentTable.TABLE);
    }
}
