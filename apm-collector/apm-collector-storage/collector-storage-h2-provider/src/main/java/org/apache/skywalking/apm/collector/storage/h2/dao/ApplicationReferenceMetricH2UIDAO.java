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

import com.google.gson.JsonArray;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationReferenceMetricUIDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ApplicationReferenceMetricH2UIDAO extends H2DAO implements IApplicationReferenceMetricUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationReferenceMetricH2UIDAO.class);
    private static final String APPLICATION_REFERENCE_SQL = "select {8}, {9}, sum({0}) as {0}, sum({1}) as {1}, sum({2}) as {2}, " +
        "sum({3}) as {3}, sum({4}) as {4}, sum({5}) as {5} from {6} where {7} >= ? and {7} <= ? group by {8}, {9} limit 100";

    public ApplicationReferenceMetricH2UIDAO(H2Client client) {
        super(client);
    }

    @Override public JsonArray load(long startTime, long endTime) {
        H2Client client = getClient();
        JsonArray applicationReferenceMetricArray = new JsonArray();
//        String sql = SqlBuilder.buildSql(APPLICATION_REFERENCE_SQL, ApplicationReferenceMetricTable.COLUMN_S1_LTE,
//            ApplicationReferenceMetricTable.COLUMN_S3_LTE, ApplicationReferenceMetricTable.COLUMN_S5_LTE,
//            ApplicationReferenceMetricTable.COLUMN_S5_GT, ApplicationReferenceMetricTable.COLUMN_SUMMARY,
//            ApplicationReferenceMetricTable.COLUMN_ERROR, ApplicationReferenceMetricTable.TABLE, ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET,
//            ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID);
//
//        Object[] params = new Object[] {startTime, endTime};
//        try (ResultSet rs = client.executeQuery(sql, params)) {
//            while (rs.next()) {
//                int frontApplicationId = rs.getInt(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID);
//                int behindApplicationId = rs.getInt(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID);
//                JsonObject nodeRefResSumObj = new JsonObject();
//                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID), frontApplicationId);
//                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID), behindApplicationId);
//                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(ApplicationReferenceMetricTable.COLUMN_S1_LTE), rs.getDouble(ApplicationReferenceMetricTable.COLUMN_S1_LTE));
//                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(ApplicationReferenceMetricTable.COLUMN_S3_LTE), rs.getDouble(ApplicationReferenceMetricTable.COLUMN_S3_LTE));
//                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(ApplicationReferenceMetricTable.COLUMN_S5_LTE), rs.getDouble(ApplicationReferenceMetricTable.COLUMN_S5_LTE));
//                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(ApplicationReferenceMetricTable.COLUMN_S5_GT), rs.getDouble(ApplicationReferenceMetricTable.COLUMN_S5_GT));
//                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(ApplicationReferenceMetricTable.COLUMN_ERROR), rs.getDouble(ApplicationReferenceMetricTable.COLUMN_ERROR));
//                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(ApplicationReferenceMetricTable.COLUMN_SUMMARY), rs.getDouble(ApplicationReferenceMetricTable.COLUMN_SUMMARY));
//                nodeRefResSumArray.add(nodeRefResSumObj);
//            }
//        } catch (SQLException | H2ClientException e) {
//            logger.error(e.getMessage(), e);
//        }
        return applicationReferenceMetricArray;
    }
}
