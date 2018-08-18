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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.imp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.MetricTransformUtil;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable;

/**
 * @author linjiaqi
 */
public abstract class AbstractInstanceMetricShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<InstanceMetric> {

    AbstractInstanceMetricShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override protected final String timeBucketColumnNameForDelete() {
        return InstanceMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final InstanceMetric shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceMetric instanceMetric = new InstanceMetric();

        instanceMetric.setId(resultSet.getString(InstanceMetricTable.ID.getName()));
        instanceMetric.setMetricId(resultSet.getString(InstanceMetricTable.METRIC_ID.getName()));
        instanceMetric.setApplicationId(resultSet.getInt(InstanceMetricTable.APPLICATION_ID.getName()));
        instanceMetric.setInstanceId(resultSet.getInt(InstanceMetricTable.INSTANCE_ID.getName()));

        MetricTransformUtil.INSTANCE.shardingjdbcDataToStreamData(resultSet, instanceMetric);
        return instanceMetric;
    }

    @Override protected final Map<String, Object> streamDataToShardingjdbcData(InstanceMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceMetricTable.ID.getName(), streamData.getId());
        target.put(InstanceMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(InstanceMetricTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(InstanceMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());

        MetricTransformUtil.INSTANCE.streamDataToShardingjdbcData(streamData, target);

        return target;
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + InstanceMetricTable.TABLE)
    @Override public final InstanceMetric get(String id) {
        return super.get(id);
    }
}
