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

package org.apache.skywalking.apm.collector.storage.es.dao;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentDurationPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDuration;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDurationTable;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentDurationEsPersistenceDAO extends EsDAO implements ISegmentDurationPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, SegmentDuration> {

    private final Logger logger = LoggerFactory.getLogger(SegmentDurationEsPersistenceDAO.class);

    public SegmentDurationEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public SegmentDuration get(String id) {
        return null;
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(SegmentDuration data) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(SegmentDuration data) {
        logger.debug("segment cost prepareBatchInsert, getApplicationId: {}", data.getId());
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
        return getClient().prepareIndex(SegmentDurationTable.TABLE, data.getId()).setSource(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(SegmentDurationTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(SegmentDurationTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, SegmentDurationTable.TABLE);
    }
}
