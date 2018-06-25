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

package org.apache.skywalking.apm.collector.storage.es.dao.acp;

import java.io.IOException;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.application.*;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractApplicationComponentEsPersistenceDAO extends AbstractPersistenceEsDAO<ApplicationComponent> {

    AbstractApplicationComponentEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return ApplicationComponentTable.TIME_BUCKET.getName();
    }

    @Override protected final ApplicationComponent esDataToStreamData(Map<String, Object> source) {
        ApplicationComponent applicationComponent = new ApplicationComponent();
        applicationComponent.setMetricId((String)source.get(ApplicationComponentTable.METRIC_ID.getName()));

        applicationComponent.setComponentId(((Number)source.get(ApplicationComponentTable.COMPONENT_ID.getName())).intValue());
        applicationComponent.setApplicationId(((Number)source.get(ApplicationComponentTable.APPLICATION_ID.getName())).intValue());
        applicationComponent.setTimeBucket(((Number)source.get(ApplicationComponentTable.TIME_BUCKET.getName())).longValue());
        return applicationComponent;
    }

    @Override protected final XContentBuilder esStreamDataToEsData(ApplicationComponent streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(ApplicationComponentTable.METRIC_ID.getName(), streamData.getMetricId())

            .field(ApplicationComponentTable.COMPONENT_ID.getName(), streamData.getComponentId())
            .field(ApplicationComponentTable.APPLICATION_ID.getName(), streamData.getApplicationId())
            .field(ApplicationComponentTable.TIME_BUCKET.getName(), streamData.getTimeBucket())
            .endObject();
    }

    @GraphComputingMetric(name = "/persistence/get/" + ApplicationComponentTable.TABLE)
    @Override public final ApplicationComponent get(String id) {
        return super.get(id);
    }
}
