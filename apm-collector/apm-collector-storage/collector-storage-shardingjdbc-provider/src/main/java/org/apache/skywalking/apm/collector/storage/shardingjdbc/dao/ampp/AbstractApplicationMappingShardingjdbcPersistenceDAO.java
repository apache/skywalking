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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.ampp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMapping;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMappingTable;

/**
 * @author linjiaqi
 */
public abstract class AbstractApplicationMappingShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<ApplicationMapping> {

    AbstractApplicationMappingShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override protected final String timeBucketColumnNameForDelete() {
        return ApplicationMappingTable.TIME_BUCKET.getName();
    }

    @Override protected final ApplicationMapping shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationMapping applicationMapping = new ApplicationMapping();
        applicationMapping.setId(resultSet.getString(ApplicationMappingTable.ID.getName()));
        applicationMapping.setMetricId(resultSet.getString(ApplicationMappingTable.METRIC_ID.getName()));

        applicationMapping.setApplicationId(resultSet.getInt(ApplicationMappingTable.APPLICATION_ID.getName()));
        applicationMapping.setMappingApplicationId(resultSet.getInt(ApplicationMappingTable.MAPPING_APPLICATION_ID.getName()));
        applicationMapping.setTimeBucket(resultSet.getLong(ApplicationMappingTable.TIME_BUCKET.getName()));
        return applicationMapping;
    }

    @Override protected final Map<String, Object> streamDataToShardingjdbcData(ApplicationMapping streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ApplicationMappingTable.ID.getName(), streamData.getId());
        target.put(ApplicationMappingTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(ApplicationMappingTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(ApplicationMappingTable.MAPPING_APPLICATION_ID.getName(), streamData.getMappingApplicationId());
        target.put(ApplicationMappingTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return target;
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + ApplicationMappingTable.TABLE)
    @Override public final ApplicationMapping get(String id) {
        return super.get(id);
    }
}
