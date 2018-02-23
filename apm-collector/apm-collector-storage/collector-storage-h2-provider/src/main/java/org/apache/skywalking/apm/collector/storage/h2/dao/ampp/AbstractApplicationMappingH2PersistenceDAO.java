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

package org.apache.skywalking.apm.collector.storage.h2.dao.ampp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMapping;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMappingTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractApplicationMappingH2PersistenceDAO extends AbstractPersistenceH2DAO<ApplicationMapping> {

    AbstractApplicationMappingH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final ApplicationMapping h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationMapping applicationMapping = new ApplicationMapping();
        applicationMapping.setId(resultSet.getString(ApplicationMappingTable.COLUMN_ID));
        applicationMapping.setMetricId(resultSet.getString(ApplicationMappingTable.COLUMN_METRIC_ID));

        applicationMapping.setApplicationId(resultSet.getInt(ApplicationMappingTable.COLUMN_APPLICATION_ID));
        applicationMapping.setMappingApplicationId(resultSet.getInt(ApplicationMappingTable.COLUMN_MAPPING_APPLICATION_ID));
        applicationMapping.setTimeBucket(resultSet.getLong(ApplicationMappingTable.COLUMN_TIME_BUCKET));
        return applicationMapping;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(ApplicationMapping streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationMappingTable.COLUMN_ID, streamData.getId());
        source.put(ApplicationMappingTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(ApplicationMappingTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());
        source.put(ApplicationMappingTable.COLUMN_MAPPING_APPLICATION_ID, streamData.getMappingApplicationId());
        source.put(ApplicationMappingTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
