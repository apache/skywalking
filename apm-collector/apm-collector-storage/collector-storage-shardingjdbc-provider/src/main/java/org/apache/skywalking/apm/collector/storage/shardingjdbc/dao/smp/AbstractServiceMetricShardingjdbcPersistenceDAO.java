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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.smp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.MetricTransformUtil;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;

/**
 * @author linjiaqi
 */
public abstract class AbstractServiceMetricShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<ServiceMetric> {

    AbstractServiceMetricShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override protected final String timeBucketColumnNameForDelete() {
        return ServiceMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final ServiceMetric shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        ServiceMetric serviceMetric = new ServiceMetric();
        serviceMetric.setId(resultSet.getString(ServiceMetricTable.ID.getName()));
        serviceMetric.setMetricId(resultSet.getString(ServiceMetricTable.METRIC_ID.getName()));

        serviceMetric.setApplicationId(resultSet.getInt(ServiceMetricTable.APPLICATION_ID.getName()));
        serviceMetric.setInstanceId(resultSet.getInt(ServiceMetricTable.INSTANCE_ID.getName()));
        serviceMetric.setServiceId(resultSet.getInt(ServiceMetricTable.SERVICE_ID.getName()));

        MetricTransformUtil.INSTANCE.shardingjdbcDataToStreamData(resultSet, serviceMetric);

        return serviceMetric;
    }

    @Override protected final Map<String, Object> streamDataToShardingjdbcData(ServiceMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceMetricTable.ID.getName(), streamData.getId());
        target.put(ServiceMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(ServiceMetricTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(ServiceMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(ServiceMetricTable.SERVICE_ID.getName(), streamData.getServiceId());

        MetricTransformUtil.INSTANCE.streamDataToShardingjdbcData(streamData, target);

        return target;
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + ServiceMetricTable.TABLE)
    @Override public final ServiceMetric get(String id) {
        return super.get(id);
    }
}
