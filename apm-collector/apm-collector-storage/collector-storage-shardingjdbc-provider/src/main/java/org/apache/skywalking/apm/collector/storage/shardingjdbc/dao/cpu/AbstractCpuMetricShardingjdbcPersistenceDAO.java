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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.cpu;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.CpuMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.CpuMetricTable;

/**
 * @author linjiaqi
 */
public abstract class AbstractCpuMetricShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<CpuMetric> {

    AbstractCpuMetricShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override protected final String timeBucketColumnNameForDelete() {
        return CpuMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final CpuMetric shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        CpuMetric cpuMetric = new CpuMetric();
        cpuMetric.setId(resultSet.getString(CpuMetricTable.ID.getName()));
        cpuMetric.setMetricId(resultSet.getString(CpuMetricTable.METRIC_ID.getName()));

        cpuMetric.setInstanceId(resultSet.getInt(CpuMetricTable.INSTANCE_ID.getName()));

        cpuMetric.setUsagePercent(resultSet.getDouble(CpuMetricTable.USAGE_PERCENT.getName()));
        cpuMetric.setTimes(resultSet.getLong(CpuMetricTable.TIMES.getName()));
        cpuMetric.setTimeBucket(resultSet.getLong(CpuMetricTable.TIME_BUCKET.getName()));

        return cpuMetric;
    }

    @Override protected final Map<String, Object> streamDataToShardingjdbcData(CpuMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(CpuMetricTable.ID.getName(), streamData.getId());
        target.put(CpuMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(CpuMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(CpuMetricTable.USAGE_PERCENT.getName(), streamData.getUsagePercent());
        target.put(CpuMetricTable.TIMES.getName(), streamData.getTimes());
        target.put(CpuMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return target;
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + CpuMetricTable.TABLE)
    @Override public final CpuMetric get(String id) {
        return super.get(id);
    }
}
