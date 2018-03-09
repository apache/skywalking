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

package org.apache.skywalking.apm.collector.storage.es.dao.cpu;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.CpuMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.CpuMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractCpuMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<CpuMetric> {

    AbstractCpuMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return CpuMetricTable.COLUMN_TIME_BUCKET;
    }

    @Override protected final CpuMetric esDataToStreamData(Map<String, Object> source) {
        CpuMetric cpuMetric = new CpuMetric();
        cpuMetric.setMetricId((String)source.get(CpuMetricTable.COLUMN_METRIC_ID));

        cpuMetric.setInstanceId(((Number)source.get(CpuMetricTable.COLUMN_INSTANCE_ID)).intValue());

        cpuMetric.setUsagePercent(((Number)source.get(CpuMetricTable.COLUMN_USAGE_PERCENT)).doubleValue());
        cpuMetric.setTimes(((Number)source.get(CpuMetricTable.COLUMN_TIMES)).longValue());
        cpuMetric.setTimeBucket(((Number)source.get(CpuMetricTable.COLUMN_TIME_BUCKET)).longValue());

        return cpuMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(CpuMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(CpuMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(CpuMetricTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(CpuMetricTable.COLUMN_USAGE_PERCENT, streamData.getUsagePercent());
        source.put(CpuMetricTable.COLUMN_TIMES, streamData.getTimes());
        source.put(CpuMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
