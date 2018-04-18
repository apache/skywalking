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

package org.apache.skywalking.apm.collector.storage.h2.dao.amp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.MetricTransformUtil;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetric;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractApplicationMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<ApplicationMetric> {

    AbstractApplicationMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final ApplicationMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationMetric applicationMetric = new ApplicationMetric();
        applicationMetric.setId(resultSet.getString(ApplicationMetricTable.ID.getName()));
        applicationMetric.setMetricId(resultSet.getString(ApplicationMetricTable.METRIC_ID.getName()));

        applicationMetric.setApplicationId(resultSet.getInt(ApplicationMetricTable.APPLICATION_ID.getName()));

        MetricTransformUtil.INSTANCE.h2DataToStreamData(resultSet, applicationMetric);

        applicationMetric.setSatisfiedCount(resultSet.getLong(ApplicationMetricTable.SATISFIED_COUNT.getName()));
        applicationMetric.setToleratingCount(resultSet.getLong(ApplicationMetricTable.TOLERATING_COUNT.getName()));
        applicationMetric.setFrustratedCount(resultSet.getLong(ApplicationMetricTable.FRUSTRATED_COUNT.getName()));

        return applicationMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(ApplicationMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ApplicationMetricTable.ID.getName(), streamData.getId());
        target.put(ApplicationMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(ApplicationMetricTable.APPLICATION_ID.getName(), streamData.getApplicationId());

        MetricTransformUtil.INSTANCE.streamDataToH2Data(streamData, target);

        target.put(ApplicationMetricTable.SATISFIED_COUNT.getName(), streamData.getSatisfiedCount());
        target.put(ApplicationMetricTable.TOLERATING_COUNT.getName(), streamData.getToleratingCount());
        target.put(ApplicationMetricTable.FRUSTRATED_COUNT.getName(), streamData.getFrustratedCount());

        return target;
    }
}
