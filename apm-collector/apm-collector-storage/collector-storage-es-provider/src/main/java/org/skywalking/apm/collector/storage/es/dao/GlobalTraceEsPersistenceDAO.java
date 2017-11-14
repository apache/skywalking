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
import org.skywalking.apm.collector.core.UnexpectedException;
import org.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.global.GlobalTrace;
import org.skywalking.apm.collector.storage.table.global.GlobalTraceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GlobalTraceEsPersistenceDAO extends EsDAO implements IGlobalTracePersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, GlobalTrace> {

    private final Logger logger = LoggerFactory.getLogger(GlobalTraceEsPersistenceDAO.class);

    @Override public GlobalTrace get(String id) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(GlobalTrace data) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public IndexRequestBuilder prepareBatchInsert(GlobalTrace data) {
        Map<String, Object> source = new HashMap<>();
        source.put(GlobalTraceTable.COLUMN_SEGMENT_ID, data.getSegmentId());
        source.put(GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID, data.getGlobalTraceId());
        source.put(GlobalTraceTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        logger.debug("global trace source: {}", source.toString());
        return getClient().prepareIndex(GlobalTraceTable.TABLE, data.getId()).setSource(source);
    }
}
