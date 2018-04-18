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

package org.apache.skywalking.apm.collector.storage.h2.dao.impp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMapping;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMappingTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractInstanceMappingH2PersistenceDAO extends AbstractPersistenceH2DAO<InstanceMapping> {

    AbstractInstanceMappingH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final InstanceMapping h2DataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceMapping instanceMapping = new InstanceMapping();
        instanceMapping.setId(resultSet.getString(InstanceMappingTable.ID.getName()));
        instanceMapping.setMetricId(resultSet.getString(InstanceMappingTable.METRIC_ID.getName()));

        instanceMapping.setApplicationId(resultSet.getInt(InstanceMappingTable.APPLICATION_ID.getName()));
        instanceMapping.setInstanceId(resultSet.getInt(InstanceMappingTable.INSTANCE_ID.getName()));
        instanceMapping.setAddressId(resultSet.getInt(InstanceMappingTable.ADDRESS_ID.getName()));
        instanceMapping.setTimeBucket(resultSet.getLong(InstanceMappingTable.TIME_BUCKET.getName()));
        return instanceMapping;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(InstanceMapping streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceMappingTable.ID.getName(), streamData.getId());
        target.put(InstanceMappingTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(InstanceMappingTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(InstanceMappingTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(InstanceMappingTable.ADDRESS_ID.getName(), streamData.getAddressId());
        target.put(InstanceMappingTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return target;
    }
}
