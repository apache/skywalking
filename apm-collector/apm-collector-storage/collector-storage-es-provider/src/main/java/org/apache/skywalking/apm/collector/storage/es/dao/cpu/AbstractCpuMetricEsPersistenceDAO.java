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
public abstract class AbstractCpuMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<CpuMetric> {

    AbstractCpuMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return CpuMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final CpuMetric esDataToStreamData(Map<String, Object> source) {
        CpuMetric cpuMetric = new CpuMetric();
        cpuMetric.setMetricId((String)source.get(CpuMetricTable.METRIC_ID.getName()));

        cpuMetric.setInstanceId(((Number)source.get(CpuMetricTable.INSTANCE_ID.getName())).intValue());

        cpuMetric.setUsagePercent(((Number)source.get(CpuMetricTable.USAGE_PERCENT.getName())).doubleValue());
        cpuMetric.setTimes(((Number)source.get(CpuMetricTable.TIMES.getName())).longValue());
        cpuMetric.setTimeBucket(((Number)source.get(CpuMetricTable.TIME_BUCKET.getName())).longValue());

        return cpuMetric;
    }

    @Override protected final XContentBuilder esStreamDataToEsData(CpuMetric streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(CpuMetricTable.METRIC_ID.getName(), streamData.getMetricId())

            .field(CpuMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId())
            .field(CpuMetricTable.USAGE_PERCENT.getName(), streamData.getUsagePercent())
            .field(CpuMetricTable.TIMES.getName(), streamData.getTimes())
            .field(CpuMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket())
            .endObject();
    }

    @GraphComputingMetric(name = "/persistence/get/" + CpuMetricTable.TABLE)
    @Override public final CpuMetric get(String id) {
        return super.get(id);
    }
}
