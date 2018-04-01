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
        return GCMetricTable.COLUMN_TIME_BUCKET;
    }

    @Override protected final GCMetric esDataToStreamData(Map<String, Object> source) {
        GCMetric gcMetric = new GCMetric();
        gcMetric.setMetricId((String)source.get(GCMetricTable.COLUMN_METRIC_ID));

        gcMetric.setInstanceId(((Number)source.get(GCMetricTable.COLUMN_INSTANCE_ID)).intValue());
        gcMetric.setPhrase(((Number)source.get(GCMetricTable.COLUMN_PHRASE)).intValue());

        gcMetric.setCount(((Number)source.get(GCMetricTable.COLUMN_COUNT)).longValue());
        gcMetric.setTimes(((Number)source.get(GCMetricTable.COLUMN_TIMES)).longValue());

        gcMetric.setTimeBucket(((Number)source.get(GCMetricTable.COLUMN_TIME_BUCKET)).longValue());

        return gcMetric;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(GCMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(GCMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(GCMetricTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(GCMetricTable.COLUMN_PHRASE, streamData.getPhrase());
        source.put(GCMetricTable.COLUMN_COUNT, streamData.getCount());
        source.put(GCMetricTable.COLUMN_TIMES, streamData.getTimes());
        source.put(GCMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
