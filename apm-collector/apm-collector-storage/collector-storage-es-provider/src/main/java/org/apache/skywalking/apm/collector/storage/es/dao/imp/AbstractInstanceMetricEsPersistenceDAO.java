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

package org.apache.skywalking.apm.collector.storage.es.dao.imp;

import java.io.IOException;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.es.MetricTransformUtil;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.instance.*;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractInstanceMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<InstanceMetric> {

    AbstractInstanceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return InstanceMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final InstanceMetric esDataToStreamData(Map<String, Object> source) {
        InstanceMetric instanceMetric = new InstanceMetric();

        instanceMetric.setMetricId((String)source.get(InstanceMetricTable.METRIC_ID.getName()));
        instanceMetric.setApplicationId((Integer)source.get(InstanceMetricTable.APPLICATION_ID.getName()));
        instanceMetric.setInstanceId((Integer)source.get(InstanceMetricTable.INSTANCE_ID.getName()));

        MetricTransformUtil.INSTANCE.esDataToStreamData(source, instanceMetric);

        return instanceMetric;
    }

    @Override protected final XContentBuilder esStreamDataToEsData(InstanceMetric streamData) throws IOException {
        XContentBuilder target = XContentFactory.jsonBuilder().startObject()
            .field(InstanceMetricTable.METRIC_ID.getName(), streamData.getMetricId())
            .field(InstanceMetricTable.APPLICATION_ID.getName(), streamData.getApplicationId())
            .field(InstanceMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());

        MetricTransformUtil.INSTANCE.esStreamDataToEsData(streamData, target);

        target.endObject();
        return target;
    }

    @GraphComputingMetric(name = "/persistence/get/" + InstanceMetricTable.TABLE)
    @Override public final InstanceMetric get(String id) {
        return super.get(id);
    }
}
