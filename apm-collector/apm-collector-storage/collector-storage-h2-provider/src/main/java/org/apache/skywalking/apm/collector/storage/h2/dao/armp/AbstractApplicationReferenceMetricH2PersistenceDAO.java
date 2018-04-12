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

package org.apache.skywalking.apm.collector.storage.h2.dao.armp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.MetricTransformUtil;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractApplicationReferenceMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<ApplicationReferenceMetric> {

    AbstractApplicationReferenceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final ApplicationReferenceMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationReferenceMetric applicationReferenceMetric = new ApplicationReferenceMetric();
        applicationReferenceMetric.setId(resultSet.getString(ApplicationReferenceMetricTable.ID.getName()));
        applicationReferenceMetric.setMetricId(resultSet.getString(ApplicationReferenceMetricTable.METRIC_ID.getName()));

        applicationReferenceMetric.setFrontApplicationId(resultSet.getInt(ApplicationReferenceMetricTable.FRONT_APPLICATION_ID.getName()));
        applicationReferenceMetric.setBehindApplicationId(resultSet.getInt(ApplicationReferenceMetricTable.BEHIND_APPLICATION_ID.getName()));

        MetricTransformUtil.INSTANCE.h2DataToStreamData(resultSet, applicationReferenceMetric);

        applicationReferenceMetric.setSatisfiedCount(resultSet.getLong(ApplicationReferenceMetricTable.SATISFIED_COUNT.getName()));
        applicationReferenceMetric.setToleratingCount(resultSet.getLong(ApplicationReferenceMetricTable.TOLERATING_COUNT.getName()));
        applicationReferenceMetric.setFrustratedCount(resultSet.getLong(ApplicationReferenceMetricTable.FRUSTRATED_COUNT.getName()));

        return applicationReferenceMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(ApplicationReferenceMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ApplicationReferenceMetricTable.ID.getName(), streamData.getId());
        target.put(ApplicationReferenceMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(ApplicationReferenceMetricTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        target.put(ApplicationReferenceMetricTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());

        MetricTransformUtil.INSTANCE.streamDataToH2Data(streamData, target);

        target.put(ApplicationReferenceMetricTable.SATISFIED_COUNT.getName(), streamData.getSatisfiedCount());
        target.put(ApplicationReferenceMetricTable.TOLERATING_COUNT.getName(), streamData.getToleratingCount());
        target.put(ApplicationReferenceMetricTable.FRUSTRATED_COUNT.getName(), streamData.getFrustratedCount());

        return target;
    }
}
