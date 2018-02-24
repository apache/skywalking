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

import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentDurationPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDuration;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDurationTable;
import io.searchbox.core.Index;
import io.searchbox.core.Update;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.core.Index;
import io.searchbox.core.Update;

/**
 * @author peng-yongsheng
 */
public class SegmentDurationEsPersistenceDAO extends EsHttpDAO implements ISegmentDurationPersistenceDAO<Index, Update, SegmentDuration> {

    private final Logger logger = LoggerFactory.getLogger(SegmentDurationEsPersistenceDAO.class);

    public SegmentDurationEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public SegmentDuration get(String id) {
        return null;
    }

    @Override public Update prepareBatchUpdate(SegmentDuration data) {
        return null;
    }

    @Override public Index prepareBatchInsert(SegmentDuration data) {
        logger.debug("segment cost prepareBatchInsert, getId: {}", data.getId());
        Map<String, Object> source = new HashMap<>();
        source.put(SegmentDurationTable.COLUMN_SEGMENT_ID, data.getSegmentId());
        source.put(SegmentDurationTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(SegmentDurationTable.COLUMN_SERVICE_NAME, data.getServiceName());
        source.put(SegmentDurationTable.COLUMN_DURATION, data.getDuration());
        source.put(SegmentDurationTable.COLUMN_START_TIME, data.getStartTime());
        source.put(SegmentDurationTable.COLUMN_END_TIME, data.getEndTime());
        source.put(SegmentDurationTable.COLUMN_IS_ERROR, data.getIsError());
        source.put(SegmentDurationTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        logger.debug("segment cost source: {}", source.toString());
        return new Index.Builder(source).index(SegmentDurationTable.TABLE).id(data.getId()).build();
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(SegmentDurationTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));
        
        long deleted =getClient().batchDelete(SegmentDurationTable.TABLE, searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, SegmentDurationTable.TABLE);
    }
}
