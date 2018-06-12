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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.gc;

import java.sql.*;
import java.util.*;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.*;

/**
 * @author linjiaqi
 */
public abstract class AbstractGCMetricShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<GCMetric> {

    AbstractGCMetricShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override protected final String timeBucketColumnNameForDelete() {
        return GCMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final GCMetric shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        GCMetric gcMetric = new GCMetric();
        gcMetric.setId(resultSet.getString(GCMetricTable.ID.getName()));
        gcMetric.setMetricId(resultSet.getString(GCMetricTable.METRIC_ID.getName()));

        gcMetric.setInstanceId(resultSet.getInt(GCMetricTable.INSTANCE_ID.getName()));
        gcMetric.setPhrase(resultSet.getInt(GCMetricTable.PHRASE.getName()));

        gcMetric.setCount(resultSet.getLong(GCMetricTable.COUNT.getName()));
        gcMetric.setTimes(resultSet.getLong(GCMetricTable.TIMES.getName()));
        gcMetric.setDuration(resultSet.getLong(GCMetricTable.DURATION.getName()));

        gcMetric.setTimeBucket(resultSet.getLong(GCMetricTable.TIME_BUCKET.getName()));

        return gcMetric;
    }

    @Override protected final Map<String, Object> streamDataToShardingjdbcData(GCMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(GCMetricTable.ID.getName(), streamData.getId());
        target.put(GCMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(GCMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(GCMetricTable.PHRASE.getName(), streamData.getPhrase());
        target.put(GCMetricTable.COUNT.getName(), streamData.getCount());
        target.put(GCMetricTable.TIMES.getName(), streamData.getTimes());
        target.put(GCMetricTable.DURATION.getName(), streamData.getDuration());
        target.put(GCMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return target;
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + GCMetricTable.TABLE)
    @Override public final GCMetric get(String id) {
        return super.get(id);
    }
}
