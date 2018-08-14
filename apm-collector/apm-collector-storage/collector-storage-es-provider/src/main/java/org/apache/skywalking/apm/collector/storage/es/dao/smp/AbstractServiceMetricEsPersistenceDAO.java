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

package org.apache.skywalking.apm.collector.storage.es.dao.smp;

import java.io.IOException;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.es.MetricTransformUtil;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.service.*;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractServiceMetricEsPersistenceDAO extends AbstractPersistenceEsDAO<ServiceMetric> {

    AbstractServiceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return ServiceMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final ServiceMetric esDataToStreamData(Map<String, Object> source) {
        ServiceMetric serviceMetric = new ServiceMetric();
        serviceMetric.setMetricId((String)source.get(ServiceMetricTable.METRIC_ID.getName()));

        serviceMetric.setApplicationId(((Number)source.get(ServiceMetricTable.APPLICATION_ID.getName())).intValue());
        serviceMetric.setInstanceId(((Number)source.get(ServiceMetricTable.INSTANCE_ID.getName())).intValue());
        serviceMetric.setServiceId(((Number)source.get(ServiceMetricTable.SERVICE_ID.getName())).intValue());

        MetricTransformUtil.INSTANCE.esDataToStreamData(source, serviceMetric);

        return serviceMetric;
    }

    @Override protected final XContentBuilder esStreamDataToEsData(ServiceMetric streamData) throws IOException {
        XContentBuilder target = XContentFactory.jsonBuilder().startObject()
            .field(ServiceMetricTable.METRIC_ID.getName(), streamData.getMetricId())

            .field(ServiceMetricTable.APPLICATION_ID.getName(), streamData.getApplicationId())
            .field(ServiceMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId())
            .field(ServiceMetricTable.SERVICE_ID.getName(), streamData.getServiceId());

        MetricTransformUtil.INSTANCE.esStreamDataToEsData(streamData, target);

        target.endObject();
        return target;
    }

    @GraphComputingMetric(name = "/persistence/get/" + ServiceMetricTable.TABLE)
    @Override public final ServiceMetric get(String id) {
        return super.get(id);
    }
}
