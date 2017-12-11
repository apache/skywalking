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


package org.apache.skywalking.apm.collector.storage.h2.dao;

import com.google.gson.JsonObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.apache.skywalking.apm.collector.storage.dao.IServiceReferenceUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ServiceReferenceH2UIDAO extends H2DAO implements IServiceReferenceUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceH2UIDAO.class);

    public ServiceReferenceH2UIDAO(H2Client client) {
        super(client);
    }

    private static final String GET_SRV_REF_LOAD1 = "select {3}, {4}, sum({5}) as {5}, sum({6}) as {6}, sum({7}) as {7}" +
        ",sum({8}) as {8} from {0} where {1} >= ? and {1} <= ? and {2} = ? group by {3}, {4}";

    @Override
    public Map<String, JsonObject> load(int entryServiceId, long startTime, long endTime) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SRV_REF_LOAD1, ServiceReferenceMetricTable.TABLE,
            ServiceReferenceMetricTable.COLUMN_TIME_BUCKET, ServiceReferenceMetricTable.COLUMN_ENTRY_SERVICE_ID,
            ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID, ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID,
            ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS, ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM,
            ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM);
        Object[] params = new Object[] {startTime, endTime, entryServiceId};

        return load(client, params, sql);
    }

    private Map<String, JsonObject> load(H2Client client, Object[] params, String sql) {
        Map<String, JsonObject> serviceReferenceMap = new LinkedHashMap<>();

        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                int frontServiceId = rs.getInt(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID);
                parseSubAggregate(serviceReferenceMap, rs, frontServiceId);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return serviceReferenceMap;
    }

    private void parseSubAggregate(Map<String, JsonObject> serviceReferenceMap, ResultSet rs,
        int frontServiceId) {
        try {
            int behindServiceId = rs.getInt(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID);
            if (behindServiceId != 0) {
                long calls = rs.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS);
                long errorCalls = rs.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS);
                long durationSum = rs.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM);
                long errorDurationSum = rs.getLong(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM);

                JsonObject serviceReference = new JsonObject();
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID), frontServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID), behindServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS), calls);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS), errorCalls);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM), durationSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM), errorDurationSum);

                String id = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID));
                serviceReferenceMap.put(id, serviceReference);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
