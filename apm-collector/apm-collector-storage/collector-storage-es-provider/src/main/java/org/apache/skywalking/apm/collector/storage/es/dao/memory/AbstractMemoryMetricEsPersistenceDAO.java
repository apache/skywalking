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
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
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
        return MemoryMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final MemoryMetric esDataToStreamData(Map<String, Object> source) {
        MemoryMetric memoryMetric = new MemoryMetric();
        memoryMetric.setMetricId((String)source.get(MemoryMetricTable.METRIC_ID.getName()));

        memoryMetric.setInstanceId(((Number)source.get(MemoryMetricTable.INSTANCE_ID.getName())).intValue());
        memoryMetric.setIsHeap(((Number)source.get(MemoryMetricTable.IS_HEAP.getName())).intValue());

        memoryMetric.setInit(((Number)source.get(MemoryMetricTable.INIT.getName())).longValue());
        memoryMetric.setMax(((Number)source.get(MemoryMetricTable.MAX.getName())).longValue());
        memoryMetric.setUsed(((Number)source.get(MemoryMetricTable.USED.getName())).longValue());
        memoryMetric.setCommitted(((Number)source.get(MemoryMetricTable.COMMITTED.getName())).longValue());
        memoryMetric.setTimes(((Number)source.get(MemoryMetricTable.TIMES.getName())).longValue());

        memoryMetric.setTimeBucket(((Number)source.get(MemoryMetricTable.TIME_BUCKET.getName())).longValue());
        return memoryMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(MemoryMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(MemoryMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(MemoryMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(MemoryMetricTable.IS_HEAP.getName(), streamData.getIsHeap());
        target.put(MemoryMetricTable.INIT.getName(), streamData.getInit());
        target.put(MemoryMetricTable.MAX.getName(), streamData.getMax());
        target.put(MemoryMetricTable.USED.getName(), streamData.getUsed());
        target.put(MemoryMetricTable.COMMITTED.getName(), streamData.getCommitted());
        target.put(MemoryMetricTable.TIMES.getName(), streamData.getTimes());
        target.put(MemoryMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return target;
    }

    @GraphComputingMetric(name = "/persistence/get/" + MemoryMetricTable.TABLE)
    @Override public final MemoryMetric get(String id) {
        return super.get(id);
    }
}
