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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.impp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMapping;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMappingTable;

/**
 * @author linjiaqi
 */
public abstract class AbstractInstanceMappingShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<InstanceMapping> {

    AbstractInstanceMappingShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override protected final String timeBucketColumnNameForDelete() {
        return InstanceMappingTable.TIME_BUCKET.getName();
    }

    @Override protected final InstanceMapping shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceMapping instanceMapping = new InstanceMapping();
        instanceMapping.setId(resultSet.getString(InstanceMappingTable.ID.getName()));
        instanceMapping.setMetricId(resultSet.getString(InstanceMappingTable.METRIC_ID.getName()));

        instanceMapping.setApplicationId(resultSet.getInt(InstanceMappingTable.APPLICATION_ID.getName()));
        instanceMapping.setInstanceId(resultSet.getInt(InstanceMappingTable.INSTANCE_ID.getName()));
        instanceMapping.setAddressId(resultSet.getInt(InstanceMappingTable.ADDRESS_ID.getName()));
        instanceMapping.setTimeBucket(resultSet.getLong(InstanceMappingTable.TIME_BUCKET.getName()));
        return instanceMapping;
    }

    @Override protected final Map<String, Object> streamDataToShardingjdbcData(InstanceMapping streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceMappingTable.ID.getName(), streamData.getId());
        target.put(InstanceMappingTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(InstanceMappingTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(InstanceMappingTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(InstanceMappingTable.ADDRESS_ID.getName(), streamData.getAddressId());
        target.put(InstanceMappingTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return target;
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + InstanceMappingTable.TABLE)
    @Override public final InstanceMapping get(String id) {
        return super.get(id);
    }
}
