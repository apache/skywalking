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

package org.apache.skywalking.apm.collector.storage.h2.dao.acp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponent;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractApplicationComponentH2PersistenceDAO extends AbstractPersistenceH2DAO<ApplicationComponent> {

    AbstractApplicationComponentH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final ApplicationComponent h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationComponent applicationComponent = new ApplicationComponent();
        applicationComponent.setId(resultSet.getString(ApplicationComponentTable.COLUMN_ID));
        applicationComponent.setMetricId(resultSet.getString(ApplicationComponentTable.COLUMN_METRIC_ID));

        applicationComponent.setComponentId(resultSet.getInt(ApplicationComponentTable.COLUMN_COMPONENT_ID));
        applicationComponent.setApplicationId(resultSet.getInt(ApplicationComponentTable.COLUMN_APPLICATION_ID));
        applicationComponent.setTimeBucket(resultSet.getLong(ApplicationComponentTable.COLUMN_TIME_BUCKET));
        return applicationComponent;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(ApplicationComponent streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationComponentTable.COLUMN_ID, streamData.getId());
        source.put(ApplicationComponentTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(ApplicationComponentTable.COLUMN_COMPONENT_ID, streamData.getComponentId());
        source.put(ApplicationComponentTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());
        source.put(ApplicationComponentTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
