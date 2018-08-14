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

package org.apache.skywalking.apm.collector.storage.es.dao.ampp;

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
public abstract class AbstractApplicationMappingEsPersistenceDAO extends AbstractPersistenceEsDAO<ApplicationMapping> {

    AbstractApplicationMappingEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return ApplicationMappingTable.TIME_BUCKET.getName();
    }

    @Override protected final ApplicationMapping esDataToStreamData(Map<String, Object> source) {
        ApplicationMapping applicationMapping = new ApplicationMapping();
        applicationMapping.setMetricId((String)source.get(ApplicationMappingTable.METRIC_ID.getName()));

        applicationMapping.setApplicationId(((Number)source.get(ApplicationMappingTable.APPLICATION_ID.getName())).intValue());
        applicationMapping.setMappingApplicationId(((Number)source.get(ApplicationMappingTable.MAPPING_APPLICATION_ID.getName())).intValue());
        applicationMapping.setTimeBucket(((Number)source.get(ApplicationMappingTable.TIME_BUCKET.getName())).longValue());
        return applicationMapping;
    }

    @Override protected final XContentBuilder esStreamDataToEsData(ApplicationMapping streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(ApplicationMappingTable.METRIC_ID.getName(), streamData.getMetricId())

            .field(ApplicationMappingTable.APPLICATION_ID.getName(), streamData.getApplicationId())
            .field(ApplicationMappingTable.MAPPING_APPLICATION_ID.getName(), streamData.getMappingApplicationId())
            .field(ApplicationMappingTable.TIME_BUCKET.getName(), streamData.getTimeBucket())
            .endObject();
    }

    @GraphComputingMetric(name = "/persistence/get/" + ApplicationMappingTable.TABLE)
    @Override public final ApplicationMapping get(String id) {
        return super.get(id);
    }
}
