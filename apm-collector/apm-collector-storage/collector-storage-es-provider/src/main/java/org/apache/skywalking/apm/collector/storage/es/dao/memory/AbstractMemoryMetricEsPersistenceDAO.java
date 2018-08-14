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

import java.io.IOException;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.*;
import org.elasticsearch.common.xcontent.*;

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

    @Override protected final XContentBuilder esStreamDataToEsData(MemoryMetric streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(MemoryMetricTable.METRIC_ID.getName(), streamData.getMetricId())

            .field(MemoryMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId())
            .field(MemoryMetricTable.IS_HEAP.getName(), streamData.getIsHeap())
            .field(MemoryMetricTable.INIT.getName(), streamData.getInit())
            .field(MemoryMetricTable.MAX.getName(), streamData.getMax())
            .field(MemoryMetricTable.USED.getName(), streamData.getUsed())
            .field(MemoryMetricTable.COMMITTED.getName(), streamData.getCommitted())
            .field(MemoryMetricTable.TIMES.getName(), streamData.getTimes())
            .field(MemoryMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket())
            .endObject();
    }

    @GraphComputingMetric(name = "/persistence/get/" + MemoryMetricTable.TABLE)
    @Override public final MemoryMetric get(String id) {
        return super.get(id);
    }
}
