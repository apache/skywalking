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

package org.apache.skywalking.apm.collector.storage.es.dao.memory;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractMemoryMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<MemoryMetric> {

    AbstractMemoryMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return MemoryMetricTable.COLUMN_TIME_BUCKET;
    }

    @Override protected final MemoryMetric esDataToStreamData(Map<String, Object> source) {
        MemoryMetric memoryMetric = new MemoryMetric();
        memoryMetric.setMetricId((String)source.get(MemoryMetricTable.COLUMN_METRIC_ID));

        memoryMetric.setInstanceId(((Number)source.get(MemoryMetricTable.COLUMN_INSTANCE_ID)).intValue());
        memoryMetric.setIsHeap(((Number)source.get(MemoryMetricTable.COLUMN_IS_HEAP)).intValue());

        memoryMetric.setInit(((Number)source.get(MemoryMetricTable.COLUMN_INIT)).longValue());
        memoryMetric.setMax(((Number)source.get(MemoryMetricTable.COLUMN_MAX)).longValue());
        memoryMetric.setUsed(((Number)source.get(MemoryMetricTable.COLUMN_USED)).longValue());
        memoryMetric.setCommitted(((Number)source.get(MemoryMetricTable.COLUMN_COMMITTED)).longValue());
        memoryMetric.setTimes(((Number)source.get(MemoryMetricTable.COLUMN_TIMES)).longValue());

        memoryMetric.setTimeBucket(((Number)source.get(MemoryMetricTable.COLUMN_TIME_BUCKET)).longValue());
        return memoryMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(MemoryMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(MemoryMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(MemoryMetricTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(MemoryMetricTable.COLUMN_IS_HEAP, streamData.getIsHeap());
        source.put(MemoryMetricTable.COLUMN_INIT, streamData.getInit());
        source.put(MemoryMetricTable.COLUMN_MAX, streamData.getMax());
        source.put(MemoryMetricTable.COLUMN_USED, streamData.getUsed());
        source.put(MemoryMetricTable.COLUMN_COMMITTED, streamData.getCommitted());
        source.put(MemoryMetricTable.COLUMN_TIMES, streamData.getTimes());
        source.put(MemoryMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
