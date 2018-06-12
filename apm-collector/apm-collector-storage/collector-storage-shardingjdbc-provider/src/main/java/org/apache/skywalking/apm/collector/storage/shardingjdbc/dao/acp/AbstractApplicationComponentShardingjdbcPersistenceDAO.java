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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.acp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponent;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;

/**
 * @author linjiaqi
 */
public abstract class AbstractApplicationComponentShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<ApplicationComponent> {

    AbstractApplicationComponentShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override protected final String timeBucketColumnNameForDelete() {
        return ApplicationComponentTable.TIME_BUCKET.getName();
    }

    @Override protected final ApplicationComponent shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationComponent applicationComponent = new ApplicationComponent();
        applicationComponent.setId(resultSet.getString(ApplicationComponentTable.ID.getName()));
        applicationComponent.setMetricId(resultSet.getString(ApplicationComponentTable.METRIC_ID.getName()));

        applicationComponent.setComponentId(resultSet.getInt(ApplicationComponentTable.COMPONENT_ID.getName()));
        applicationComponent.setApplicationId(resultSet.getInt(ApplicationComponentTable.APPLICATION_ID.getName()));
        applicationComponent.setTimeBucket(resultSet.getLong(ApplicationComponentTable.TIME_BUCKET.getName()));
        return applicationComponent;
    }

    @Override protected final Map<String, Object> streamDataToShardingjdbcData(ApplicationComponent streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ApplicationComponentTable.ID.getName(), streamData.getId());
        target.put(ApplicationComponentTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(ApplicationComponentTable.COMPONENT_ID.getName(), streamData.getComponentId());
        target.put(ApplicationComponentTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(ApplicationComponentTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return target;
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + ApplicationComponentTable.TABLE)
    @Override public final ApplicationComponent get(String id) {
        return super.get(id);
    }
}
