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

package org.apache.skywalking.apm.collector.storage.es.dao.gc;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractGCMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<GCMetric> {

    AbstractGCMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return GCMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final GCMetric esDataToStreamData(Map<String, Object> source) {
        GCMetric gcMetric = new GCMetric();
        gcMetric.setMetricId((String)source.get(GCMetricTable.METRIC_ID.getName()));

        gcMetric.setInstanceId(((Number)source.get(GCMetricTable.INSTANCE_ID.getName())).intValue());
        gcMetric.setPhrase(((Number)source.get(GCMetricTable.PHRASE.getName())).intValue());

        gcMetric.setCount(((Number)source.get(GCMetricTable.COUNT.getName())).longValue());
        gcMetric.setTimes(((Number)source.get(GCMetricTable.TIMES.getName())).longValue());

        gcMetric.setTimeBucket(((Number)source.get(GCMetricTable.TIME_BUCKET.getName())).longValue());

        return gcMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(GCMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(GCMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(GCMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(GCMetricTable.PHRASE.getName(), streamData.getPhrase());
        target.put(GCMetricTable.COUNT.getName(), streamData.getCount());
        target.put(GCMetricTable.TIMES.getName(), streamData.getTimes());
        target.put(GCMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return target;
    }

    @GraphComputingMetric(name = "/persistence/get/" + GCMetricTable.TABLE)
    @Override public final GCMetric get(String id) {
        return super.get(id);
    }
}
