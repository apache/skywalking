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

package org.apache.skywalking.apm.collector.storage.es.dao.mpool;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractMemoryPoolMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<MemoryPoolMetric> {

    AbstractMemoryPoolMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return MemoryPoolMetricTable.COLUMN_TIME_BUCKET;
    }

    @Override protected final MemoryPoolMetric esDataToStreamData(Map<String, Object> source) {
        MemoryPoolMetric memoryPoolMetric = new MemoryPoolMetric();
        memoryPoolMetric.setMetricId((String)source.get(MemoryPoolMetricTable.COLUMN_METRIC_ID));

        memoryPoolMetric.setInstanceId(((Number)source.get(MemoryPoolMetricTable.COLUMN_INSTANCE_ID)).intValue());
        memoryPoolMetric.setPoolType(((Number)source.get(MemoryPoolMetricTable.COLUMN_POOL_TYPE)).intValue());

        memoryPoolMetric.setInit(((Number)source.get(MemoryPoolMetricTable.COLUMN_INIT)).longValue());
        memoryPoolMetric.setMax(((Number)source.get(MemoryPoolMetricTable.COLUMN_MAX)).longValue());
        memoryPoolMetric.setUsed(((Number)source.get(MemoryPoolMetricTable.COLUMN_USED)).longValue());
        memoryPoolMetric.setCommitted(((Number)source.get(MemoryPoolMetricTable.COLUMN_COMMITTED)).longValue());
        memoryPoolMetric.setTimes(((Number)source.get(MemoryPoolMetricTable.COLUMN_TIMES)).longValue());

        memoryPoolMetric.setTimeBucket(((Number)source.get(MemoryPoolMetricTable.COLUMN_TIME_BUCKET)).longValue());
        return memoryPoolMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(MemoryPoolMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(MemoryPoolMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(MemoryPoolMetricTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(MemoryPoolMetricTable.COLUMN_POOL_TYPE, streamData.getPoolType());
        source.put(MemoryPoolMetricTable.COLUMN_INIT, streamData.getInit());
        source.put(MemoryPoolMetricTable.COLUMN_MAX, streamData.getMax());
        source.put(MemoryPoolMetricTable.COLUMN_USED, streamData.getUsed());
        source.put(MemoryPoolMetricTable.COLUMN_COMMITTED, streamData.getCommitted());
        source.put(MemoryPoolMetricTable.COLUMN_TIMES, streamData.getTimes());
        source.put(MemoryPoolMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
