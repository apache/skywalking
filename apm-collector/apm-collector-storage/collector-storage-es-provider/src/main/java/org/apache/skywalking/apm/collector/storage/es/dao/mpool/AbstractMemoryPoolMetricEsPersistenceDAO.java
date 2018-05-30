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
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
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

    @Override
    protected final String timeBucketColumnNameForDelete() {
        return MemoryPoolMetricTable.TIME_BUCKET.getName();
    }

    @Override
    protected final MemoryPoolMetric esDataToStreamData(Map<String, Object> source) {
        MemoryPoolMetric memoryPoolMetric = new MemoryPoolMetric();
        memoryPoolMetric.setMetricId((String)source.get(MemoryPoolMetricTable.METRIC_ID.getName()));

        memoryPoolMetric.setInstanceId(((Number)source.get(MemoryPoolMetricTable.INSTANCE_ID.getName())).intValue());
        memoryPoolMetric.setPoolType(((Number)source.get(MemoryPoolMetricTable.POOL_TYPE.getName())).intValue());

        memoryPoolMetric.setInit(((Number)source.get(MemoryPoolMetricTable.INIT.getName())).longValue());
        memoryPoolMetric.setMax(((Number)source.get(MemoryPoolMetricTable.MAX.getName())).longValue());
        memoryPoolMetric.setUsed(((Number)source.get(MemoryPoolMetricTable.USED.getName())).longValue());
        memoryPoolMetric.setCommitted(((Number)source.get(MemoryPoolMetricTable.COMMITTED.getName())).longValue());
        memoryPoolMetric.setTimes(((Number)source.get(MemoryPoolMetricTable.TIMES.getName())).longValue());

        memoryPoolMetric.setTimeBucket(((Number)source.get(MemoryPoolMetricTable.TIME_BUCKET.getName())).longValue());
        return memoryPoolMetric;
    }

    @Override
    protected final Map<String, Object> esStreamDataToEsData(MemoryPoolMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(MemoryPoolMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(MemoryPoolMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(MemoryPoolMetricTable.POOL_TYPE.getName(), streamData.getPoolType());
        target.put(MemoryPoolMetricTable.INIT.getName(), streamData.getInit());
        target.put(MemoryPoolMetricTable.MAX.getName(), streamData.getMax());
        target.put(MemoryPoolMetricTable.USED.getName(), streamData.getUsed());
        target.put(MemoryPoolMetricTable.COMMITTED.getName(), streamData.getCommitted());
        target.put(MemoryPoolMetricTable.TIMES.getName(), streamData.getTimes());
        target.put(MemoryPoolMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return target;
    }

    @GraphComputingMetric(name = "/persistence/get/" + MemoryPoolMetricTable.TABLE)
    @Override public final MemoryPoolMetric get(String id) {
        return super.get(id);
    }
}
