/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.es.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.ISegmentCostDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.segment.SegmentCost;
import org.skywalking.apm.collector.storage.table.segment.SegmentCostTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentCostEsDAO extends EsDAO implements ISegmentCostDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, SegmentCost> {

    private final Logger logger = LoggerFactory.getLogger(SegmentCostEsDAO.class);

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
}
