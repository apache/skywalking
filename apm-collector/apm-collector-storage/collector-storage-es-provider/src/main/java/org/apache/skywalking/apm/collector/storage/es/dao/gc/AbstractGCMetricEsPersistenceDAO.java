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
        gcMetric.setDuration(((Number)source.get(GCMetricTable.DURATION.getName())).longValue());

        gcMetric.setTimeBucket(((Number)source.get(GCMetricTable.TIME_BUCKET.getName())).longValue());

        return gcMetric;
    }

    @Override protected final XContentBuilder esStreamDataToEsData(GCMetric streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(GCMetricTable.METRIC_ID.getName(), streamData.getMetricId())

            .field(GCMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId())
            .field(GCMetricTable.PHRASE.getName(), streamData.getPhrase())
            .field(GCMetricTable.COUNT.getName(), streamData.getCount())
            .field(GCMetricTable.TIMES.getName(), streamData.getTimes())
            .field(GCMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket())
            .field(GCMetricTable.DURATION.getName(), streamData.getDuration())
            .endObject();
    }

    @GraphComputingMetric(name = "/persistence/get/" + GCMetricTable.TABLE)
    @Override public final GCMetric get(String id) {
        return super.get(id);
    }
}
