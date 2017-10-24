/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.skywalking.apm.collector.cache.ServiceNameCache;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceTable;
import org.skywalking.apm.collector.storage.h2.SqlBuilder;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ServiceReferenceH2DAO extends H2DAO implements IServiceReferenceDAO {
    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceH2DAO.class);

    private static final String GET_SRV_REF_LOAD1 = "select {3}, {4}, sum({5}) as {5}, sum({6}) as {6}, sum({7}) as {7}" +
        ",sum({8}) as {8}, sum({9}) as {9}, sum({10}) as {10}, sum({11}) as {11} from {0} where {1} >= ? and {1} <= ? and {2} = ? group by {3}, {4}";

    @Override public Map<String, JsonObject> load(int entryServiceId, long startTime, long endTime) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SRV_REF_LOAD1, ServiceReferenceTable.TABLE,
            ServiceReferenceTable.COLUMN_TIME_BUCKET, ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID,
            ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID,
            ServiceReferenceTable.COLUMN_S1_LTE, ServiceReferenceTable.COLUMN_S3_LTE, ServiceReferenceTable.COLUMN_S5_LTE,
            ServiceReferenceTable.COLUMN_S5_GT, ServiceReferenceTable.COLUMN_ERROR, ServiceReferenceTable.COLUMN_SUMMARY,
            ServiceReferenceTable.COLUMN_COST_SUMMARY);
        Object[] params = new Object[] {startTime, endTime, entryServiceId};

        return load(client, params, sql);
    }

    private Map<String, JsonObject> load(H2Client client, Object[] params, String sql) {
        Map<String, JsonObject> serviceReferenceMap = new LinkedHashMap<>();

        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                int frontServiceId = rs.getInt(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID);
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
            int behindServiceId = rs.getInt(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID);
            if (behindServiceId != 0) {
                long s1LteSum = rs.getLong(ServiceReferenceTable.COLUMN_S1_LTE);
                long s3LteSum = rs.getLong(ServiceReferenceTable.COLUMN_S3_LTE);
                long s5LteSum = rs.getLong(ServiceReferenceTable.COLUMN_S5_LTE);
                long s5GtSum = rs.getLong(ServiceReferenceTable.COLUMN_S5_GT);
                long error = rs.getLong(ServiceReferenceTable.COLUMN_ERROR);
                long summary = rs.getLong(ServiceReferenceTable.COLUMN_SUMMARY);
                long costSum = rs.getLong(ServiceReferenceTable.COLUMN_COST_SUMMARY);

                String frontServiceName = ServiceNameCache.getSplitServiceName(ServiceNameCache.get(frontServiceId));
                String behindServiceName = ServiceNameCache.getSplitServiceName(ServiceNameCache.get(behindServiceId));

                JsonObject serviceReference = new JsonObject();
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID), frontServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME), frontServiceName);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID), behindServiceId);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME), behindServiceName);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S1_LTE), s1LteSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S3_LTE), s3LteSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_LTE), s5LteSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_GT), s5GtSum);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_ERROR), error);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_SUMMARY), summary);
                serviceReference.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_COST_SUMMARY), costSum);

                String id = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID));
                serviceReferenceMap.put(id, serviceReference);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
