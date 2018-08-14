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

package org.apache.skywalking.apm.collector.storage.es.dao.impp;

import java.io.IOException;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.instance.*;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractInstanceMappingEsPersistenceDAO extends AbstractPersistenceEsDAO<InstanceMapping> {

    AbstractInstanceMappingEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return InstanceMappingTable.TIME_BUCKET.getName();
    }

    @Override protected final InstanceMapping esDataToStreamData(Map<String, Object> source) {
        InstanceMapping instanceMapping = new InstanceMapping();
        instanceMapping.setMetricId((String)source.get(InstanceMappingTable.METRIC_ID.getName()));

        instanceMapping.setApplicationId(((Number)source.get(InstanceMappingTable.APPLICATION_ID.getName())).intValue());
        instanceMapping.setInstanceId(((Number)source.get(InstanceMappingTable.INSTANCE_ID.getName())).intValue());
        instanceMapping.setAddressId(((Number)source.get(InstanceMappingTable.ADDRESS_ID.getName())).intValue());
        instanceMapping.setTimeBucket(((Number)source.get(InstanceMappingTable.TIME_BUCKET.getName())).longValue());
        return instanceMapping;
    }

    @Override protected final XContentBuilder esStreamDataToEsData(InstanceMapping streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(InstanceMappingTable.METRIC_ID.getName(), streamData.getMetricId())

            .field(InstanceMappingTable.APPLICATION_ID.getName(), streamData.getApplicationId())
            .field(InstanceMappingTable.INSTANCE_ID.getName(), streamData.getInstanceId())
            .field(InstanceMappingTable.ADDRESS_ID.getName(), streamData.getAddressId())
            .field(InstanceMappingTable.TIME_BUCKET.getName(), streamData.getTimeBucket())
            .endObject();
    }

    @GraphComputingMetric(name = "/persistence/get/" + InstanceMappingTable.TABLE)
    @Override public final InstanceMapping get(String id) {
        return super.get(id);
    }
}
