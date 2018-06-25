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

package org.apache.skywalking.apm.collector.storage.es.dao.irmp;

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
public abstract class AbstractInstanceReferenceMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<InstanceReferenceMetric> {

    AbstractInstanceReferenceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return InstanceReferenceMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final InstanceReferenceMetric esDataToStreamData(Map<String, Object> source) {
        InstanceReferenceMetric instanceReferenceMetric = new InstanceReferenceMetric();
        instanceReferenceMetric.setMetricId((String)source.get(InstanceReferenceMetricTable.METRIC_ID.getName()));

        instanceReferenceMetric.setFrontApplicationId((Integer)source.get(InstanceReferenceMetricTable.FRONT_APPLICATION_ID.getName()));
        instanceReferenceMetric.setBehindApplicationId((Integer)source.get(InstanceReferenceMetricTable.BEHIND_APPLICATION_ID.getName()));
        instanceReferenceMetric.setFrontInstanceId((Integer)source.get(InstanceReferenceMetricTable.FRONT_INSTANCE_ID.getName()));
        instanceReferenceMetric.setBehindInstanceId((Integer)source.get(InstanceReferenceMetricTable.BEHIND_INSTANCE_ID.getName()));

        MetricTransformUtil.INSTANCE.esDataToStreamData(source, instanceReferenceMetric);

        return instanceReferenceMetric;
    }

    @Override
    protected final XContentBuilder esStreamDataToEsData(InstanceReferenceMetric streamData) throws IOException {
        XContentBuilder target = XContentFactory.jsonBuilder().startObject()
            .field(InstanceReferenceMetricTable.METRIC_ID.getName(), streamData.getMetricId())
            .field(InstanceReferenceMetricTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId())
            .field(InstanceReferenceMetricTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId())
            .field(InstanceReferenceMetricTable.FRONT_INSTANCE_ID.getName(), streamData.getFrontInstanceId())
            .field(InstanceReferenceMetricTable.BEHIND_INSTANCE_ID.getName(), streamData.getBehindInstanceId());

        MetricTransformUtil.INSTANCE.esStreamDataToEsData(streamData, target);

        target.endObject();
        return target;
    }

    @GraphComputingMetric(name = "/persistence/get/" + InstanceReferenceMetricTable.TABLE)
    @Override public final InstanceReferenceMetric get(String id) {
        return super.get(id);
    }
}
