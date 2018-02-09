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

package org.apache.skywalking.apm.collector.storage.h2.dao.ui;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGCMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.apache.skywalking.apm.network.proto.GCPhrase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class GCMetricH2UIDAO extends H2DAO implements IGCMetricUIDAO {

    private final Logger logger = LoggerFactory.getLogger(GCMetricH2UIDAO.class);
    private static final String GET_GC_METRIC_SQL = "select * from {0} where {1} = ?";

    public GCMetricH2UIDAO(H2Client client) {
        super(client);
    }

    @Override public List<Integer> getYoungGCTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        return getGCTrend(instanceId, step, durationPoints, GCPhrase.NEW_VALUE);
    }

    @Override public List<Integer> getOldGCTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        return getGCTrend(instanceId, step, durationPoints, GCPhrase.OLD_VALUE);
    }

    private List<Integer> getGCTrend(int instanceId, Step step, List<DurationPoint> durationPoints, int gcPhrase) {
        String tableName = TimePyramidTableNameBuilder.build(step, GCMetricTable.TABLE);

        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_GC_METRIC_SQL, tableName, GCMetricTable.COLUMN_ID);

        List<Integer> gcTrends = new LinkedList<>();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + gcPhrase;
            try (ResultSet rs = client.executeQuery(sql, new String[] {id})) {
                if (rs.next()) {
                    long count = rs.getLong(GCMetricTable.COLUMN_COUNT);
                    long times = rs.getLong(GCMetricTable.COLUMN_TIMES);
                    gcTrends.add((int)(count / times));
                } else {
                    gcTrends.add(0);
                }
            } catch (SQLException | H2ClientException e) {
                logger.error(e.getMessage(), e);
            }
        });

        return gcTrends;
    }
}
