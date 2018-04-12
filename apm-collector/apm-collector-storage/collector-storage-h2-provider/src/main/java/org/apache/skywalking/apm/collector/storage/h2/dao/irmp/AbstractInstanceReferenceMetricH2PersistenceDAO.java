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

package org.apache.skywalking.apm.collector.storage.h2.dao.irmp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.MetricTransformUtil;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractInstanceReferenceMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<InstanceReferenceMetric> {

    AbstractInstanceReferenceMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final InstanceReferenceMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceReferenceMetric instanceReferenceMetric = new InstanceReferenceMetric();
        instanceReferenceMetric.setId(resultSet.getString(InstanceReferenceMetricTable.ID));
        instanceReferenceMetric.setMetricId(resultSet.getString(InstanceReferenceMetricTable.METRIC_ID));

        instanceReferenceMetric.setFrontApplicationId(resultSet.getInt(InstanceReferenceMetricTable.FRONT_APPLICATION_ID));
        instanceReferenceMetric.setBehindApplicationId(resultSet.getInt(InstanceReferenceMetricTable.BEHIND_APPLICATION_ID));
        instanceReferenceMetric.setFrontInstanceId(resultSet.getInt(InstanceReferenceMetricTable.FRONT_INSTANCE_ID));
        instanceReferenceMetric.setBehindInstanceId(resultSet.getInt(InstanceReferenceMetricTable.BEHIND_INSTANCE_ID));
        instanceReferenceMetric.setSourceValue(resultSet.getInt(InstanceReferenceMetricTable.SOURCE_VALUE));

        MetricTransformUtil.INSTANCE.h2DataToStreamData(resultSet, instanceReferenceMetric);

        instanceReferenceMetric.setTransactionAverageDuration(resultSet.getLong(InstanceReferenceMetricTable.TRANSACTION_AVERAGE_DURATION));
        instanceReferenceMetric.setBusinessTransactionAverageDuration(resultSet.getLong(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION));
        instanceReferenceMetric.setMqTransactionAverageDuration(resultSet.getLong(InstanceReferenceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION));

        instanceReferenceMetric.setTimeBucket(resultSet.getLong(InstanceReferenceMetricTable.TIME_BUCKET));
        return instanceReferenceMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(InstanceReferenceMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceMetricTable.ID, streamData.getId());
        source.put(InstanceReferenceMetricTable.METRIC_ID, streamData.getMetricId());

        source.put(InstanceReferenceMetricTable.FRONT_APPLICATION_ID, streamData.getFrontApplicationId());
        source.put(InstanceReferenceMetricTable.BEHIND_APPLICATION_ID, streamData.getBehindApplicationId());
        source.put(InstanceReferenceMetricTable.FRONT_INSTANCE_ID, streamData.getFrontInstanceId());
        source.put(InstanceReferenceMetricTable.BEHIND_INSTANCE_ID, streamData.getBehindInstanceId());
        source.put(InstanceReferenceMetricTable.SOURCE_VALUE, streamData.getSourceValue());

        source.put(InstanceReferenceMetricTable.TRANSACTION_CALLS, streamData.getTransactionCalls());
        source.put(InstanceReferenceMetricTable.TRANSACTION_ERROR_CALLS, streamData.getTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.TRANSACTION_DURATION_SUM, streamData.getTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM, streamData.getTransactionErrorDurationSum());
        source.put(InstanceReferenceMetricTable.TRANSACTION_AVERAGE_DURATION, streamData.getTransactionAverageDuration());

        source.put(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_CALLS, streamData.getBusinessTransactionCalls());
        source.put(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS, streamData.getBusinessTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM, streamData.getBusinessTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM, streamData.getBusinessTransactionErrorDurationSum());
        source.put(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION, streamData.getBusinessTransactionAverageDuration());

        source.put(InstanceReferenceMetricTable.MQ_TRANSACTION_CALLS, streamData.getMqTransactionCalls());
        source.put(InstanceReferenceMetricTable.MQ_TRANSACTION_ERROR_CALLS, streamData.getMqTransactionErrorCalls());
        source.put(InstanceReferenceMetricTable.MQ_TRANSACTION_DURATION_SUM, streamData.getMqTransactionDurationSum());
        source.put(InstanceReferenceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM, streamData.getMqTransactionErrorDurationSum());
        source.put(InstanceReferenceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION, streamData.getMqTransactionAverageDuration());

        source.put(InstanceReferenceMetricTable.TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
