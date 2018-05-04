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

package org.apache.skywalking.apm.collector.storage.h2.dao.smp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.MetricTransformUtil;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractServiceMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<ServiceMetric> {

    AbstractServiceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final ServiceMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ServiceMetric serviceMetric = new ServiceMetric();
        serviceMetric.setId(resultSet.getString(ServiceMetricTable.ID.getName()));
        serviceMetric.setMetricId(resultSet.getString(ServiceMetricTable.METRIC_ID.getName()));

        serviceMetric.setApplicationId(resultSet.getInt(ServiceMetricTable.APPLICATION_ID.getName()));
        serviceMetric.setInstanceId(resultSet.getInt(ServiceMetricTable.INSTANCE_ID.getName()));
        serviceMetric.setServiceId(resultSet.getInt(ServiceMetricTable.SERVICE_ID.getName()));

        MetricTransformUtil.INSTANCE.h2DataToStreamData(resultSet, serviceMetric);

        return serviceMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(ServiceMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceMetricTable.ID.getName(), streamData.getId());
        target.put(ServiceMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(ServiceMetricTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(ServiceMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(ServiceMetricTable.SERVICE_ID.getName(), streamData.getServiceId());

        MetricTransformUtil.INSTANCE.streamDataToH2Data(streamData, target);

        return target;
    }
}
