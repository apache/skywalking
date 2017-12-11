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
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentCostTable;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentCostEsPersistenceDAO extends EsDAO implements ISegmentCostPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, SegmentCost> {

    private final Logger logger = LoggerFactory.getLogger(SegmentCostEsPersistenceDAO.class);

    public SegmentCostEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public SegmentCost get(String id) {
        return null;
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(SegmentCost data) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(SegmentCost data) {
        logger.debug("segment cost prepareBatchInsert, getId: {}", data.getId());
        Map<String, Object> source = new HashMap<>();
        source.put(SegmentCostTable.COLUMN_SEGMENT_ID, data.getSegmentId());
        source.put(SegmentCostTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(SegmentCostTable.COLUMN_SERVICE_NAME, data.getServiceName());
        source.put(SegmentCostTable.COLUMN_COST, data.getCost());
        source.put(SegmentCostTable.COLUMN_START_TIME, data.getStartTime());
        source.put(SegmentCostTable.COLUMN_END_TIME, data.getEndTime());
        source.put(SegmentCostTable.COLUMN_IS_ERROR, data.getIsError());
        source.put(SegmentCostTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        logger.debug("segment cost source: {}", source.toString());
        return getClient().prepareIndex(SegmentCostTable.TABLE, data.getId()).setSource(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(SegmentCostTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(SegmentCostTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, SegmentCostTable.TABLE);
    }
}
