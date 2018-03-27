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

    public AbstractInstanceMappingH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final InstanceMapping h2DataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceMapping instanceMapping = new InstanceMapping();
        instanceMapping.setId(resultSet.getString(InstanceMappingTable.COLUMN_ID));
        instanceMapping.setMetricId(resultSet.getString(InstanceMappingTable.COLUMN_METRIC_ID));

        instanceMapping.setApplicationId(resultSet.getInt(InstanceMappingTable.COLUMN_APPLICATION_ID));
        instanceMapping.setInstanceId(resultSet.getInt(InstanceMappingTable.COLUMN_INSTANCE_ID));
        instanceMapping.setAddressId(resultSet.getInt(InstanceMappingTable.COLUMN_ADDRESS_ID));
        instanceMapping.setTimeBucket(resultSet.getLong(InstanceMappingTable.COLUMN_TIME_BUCKET));
        return instanceMapping;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(InstanceMapping streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceMappingTable.COLUMN_ID, streamData.getId());
        source.put(InstanceMappingTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(InstanceMappingTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());
        source.put(InstanceMappingTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(InstanceMappingTable.COLUMN_ADDRESS_ID, streamData.getAddressId());
        source.put(InstanceMappingTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
