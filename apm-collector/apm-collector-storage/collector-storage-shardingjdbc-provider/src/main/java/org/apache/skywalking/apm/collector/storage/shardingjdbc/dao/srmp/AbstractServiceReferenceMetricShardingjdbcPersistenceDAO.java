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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.srmp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.MetricTransformUtil;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;

/**
 * @author linjiaqi
 */
public abstract class AbstractServiceReferenceMetricShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<ServiceReferenceMetric> {

    AbstractServiceReferenceMetricShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override protected final String timeBucketColumnNameForDelete() {
        return ServiceReferenceMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final ServiceReferenceMetric shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        ServiceReferenceMetric serviceReferenceMetric = new ServiceReferenceMetric();
        serviceReferenceMetric.setId(resultSet.getString(ServiceReferenceMetricTable.ID.getName()));
        serviceReferenceMetric.setMetricId(resultSet.getString(ServiceReferenceMetricTable.METRIC_ID.getName()));

        serviceReferenceMetric.setFrontApplicationId(resultSet.getInt(ServiceReferenceMetricTable.FRONT_APPLICATION_ID.getName()));
        serviceReferenceMetric.setBehindApplicationId(resultSet.getInt(ServiceReferenceMetricTable.BEHIND_APPLICATION_ID.getName()));
        serviceReferenceMetric.setFrontInstanceId(resultSet.getInt(ServiceReferenceMetricTable.FRONT_INSTANCE_ID.getName()));
        serviceReferenceMetric.setBehindInstanceId(resultSet.getInt(ServiceReferenceMetricTable.BEHIND_INSTANCE_ID.getName()));
        serviceReferenceMetric.setFrontServiceId(resultSet.getInt(ServiceReferenceMetricTable.FRONT_SERVICE_ID.getName()));
        serviceReferenceMetric.setBehindServiceId(resultSet.getInt(ServiceReferenceMetricTable.BEHIND_SERVICE_ID.getName()));

        MetricTransformUtil.INSTANCE.shardingjdbcDataToStreamData(resultSet, serviceReferenceMetric);

        return serviceReferenceMetric;
    }

    @Override protected final Map<String, Object> streamDataToShardingjdbcData(ServiceReferenceMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceReferenceMetricTable.ID.getName(), streamData.getId());
        target.put(ServiceReferenceMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(ServiceReferenceMetricTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        target.put(ServiceReferenceMetricTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());
        target.put(ServiceReferenceMetricTable.FRONT_INSTANCE_ID.getName(), streamData.getFrontInstanceId());
        target.put(ServiceReferenceMetricTable.BEHIND_INSTANCE_ID.getName(), streamData.getBehindInstanceId());
        target.put(ServiceReferenceMetricTable.FRONT_SERVICE_ID.getName(), streamData.getFrontServiceId());
        target.put(ServiceReferenceMetricTable.BEHIND_SERVICE_ID.getName(), streamData.getBehindServiceId());

        MetricTransformUtil.INSTANCE.streamDataToShardingjdbcData(streamData, target);

        return target;
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + ServiceReferenceMetricTable.TABLE)
    @Override public final ServiceReferenceMetric get(String id) {
        return super.get(id);
    }
}
