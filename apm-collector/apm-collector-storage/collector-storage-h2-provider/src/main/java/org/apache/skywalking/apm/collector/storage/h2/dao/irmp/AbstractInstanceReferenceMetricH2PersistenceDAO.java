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

package org.apache.skywalking.apm.collector.storage.h2.dao.irmp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.MetricTransformUtil;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractInstanceReferenceMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<InstanceReferenceMetric> {

    AbstractInstanceReferenceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final InstanceReferenceMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceReferenceMetric instanceReferenceMetric = new InstanceReferenceMetric();
        instanceReferenceMetric.setId(resultSet.getString(InstanceReferenceMetricTable.ID.getName()));
        instanceReferenceMetric.setMetricId(resultSet.getString(InstanceReferenceMetricTable.METRIC_ID.getName()));

        instanceReferenceMetric.setFrontApplicationId(resultSet.getInt(InstanceReferenceMetricTable.FRONT_APPLICATION_ID.getName()));
        instanceReferenceMetric.setBehindApplicationId(resultSet.getInt(InstanceReferenceMetricTable.BEHIND_APPLICATION_ID.getName()));
        instanceReferenceMetric.setFrontInstanceId(resultSet.getInt(InstanceReferenceMetricTable.FRONT_INSTANCE_ID.getName()));
        instanceReferenceMetric.setBehindInstanceId(resultSet.getInt(InstanceReferenceMetricTable.BEHIND_INSTANCE_ID.getName()));

        MetricTransformUtil.INSTANCE.h2DataToStreamData(resultSet, instanceReferenceMetric);

        return instanceReferenceMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(InstanceReferenceMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceReferenceMetricTable.ID.getName(), streamData.getId());
        target.put(InstanceReferenceMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(InstanceReferenceMetricTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        target.put(InstanceReferenceMetricTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());
        target.put(InstanceReferenceMetricTable.FRONT_INSTANCE_ID.getName(), streamData.getFrontInstanceId());
        target.put(InstanceReferenceMetricTable.BEHIND_INSTANCE_ID.getName(), streamData.getBehindInstanceId());

        MetricTransformUtil.INSTANCE.streamDataToH2Data(streamData, target);

        return target;
    }
}
