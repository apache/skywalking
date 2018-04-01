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

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponent;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractApplicationComponentEsPersistenceDAO extends AbstractPersistenceEsDAO<ApplicationComponent> {

    AbstractApplicationComponentEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return ApplicationComponentTable.COLUMN_TIME_BUCKET;
    }

    @Override protected final ApplicationComponent esDataToStreamData(Map<String, Object> source) {
        ApplicationComponent applicationComponent = new ApplicationComponent();
        applicationComponent.setMetricId((String)source.get(ApplicationComponentTable.COLUMN_METRIC_ID));

        applicationComponent.setComponentId(((Number)source.get(ApplicationComponentTable.COLUMN_COMPONENT_ID)).intValue());
        applicationComponent.setApplicationId(((Number)source.get(ApplicationComponentTable.COLUMN_APPLICATION_ID)).intValue());
        applicationComponent.setTimeBucket(((Number)source.get(ApplicationComponentTable.COLUMN_TIME_BUCKET)).longValue());
        return applicationComponent;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(ApplicationComponent streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationComponentTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(ApplicationComponentTable.COLUMN_COMPONENT_ID, streamData.getComponentId());
        source.put(ApplicationComponentTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());
        source.put(ApplicationComponentTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
