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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.ui;

import java.sql.*;
import java.util.*;
import org.apache.skywalking.apm.collector.client.shardingjdbc.*;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGCMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.*;
import org.apache.skywalking.apm.network.proto.GCPhrase;
import org.slf4j.*;

/**
 * @author linjiaqi
 */
public class GCMetricShardingjdbcUIDAO extends ShardingjdbcDAO implements IGCMetricUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(GCMetricShardingjdbcUIDAO.class);
    private static final String GET_GC_METRIC_SQL = "select * from {0} where {1} = ?";

    public GCMetricShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public List<Trend> getYoungGCTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        return getGCTrend(instanceId, step, durationPoints, GCPhrase.NEW_VALUE);
    }

    @Override public List<Trend> getOldGCTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        return getGCTrend(instanceId, step, durationPoints, GCPhrase.OLD_VALUE);
    }

    private List<Trend> getGCTrend(int instanceId, Step step, List<DurationPoint> durationPoints, int gcPhrase) {
        String tableName = TimePyramidTableNameBuilder.build(step, GCMetricTable.TABLE);

        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(GET_GC_METRIC_SQL, tableName, GCMetricTable.ID.getName());

        List<Trend> gcTrends = new LinkedList<>();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + gcPhrase;
            try (
                ResultSet rs = client.executeQuery(sql, new String[] {id});
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
                if (rs.next()) {
                    long count = rs.getLong(GCMetricTable.COUNT.getName());
                    long duration = rs.getLong(GCMetricTable.DURATION.getName());
                    long times = rs.getLong(GCMetricTable.TIMES.getName());
                    gcTrends.add(new Trend((int)count, (int)(duration / times)));
                } else {
                    gcTrends.add(new Trend(0, 0));
                }
            } catch (SQLException | ShardingjdbcClientException e) {
                logger.error(e.getMessage(), e);
                gcTrends.add(new Trend(0, 0));
            }
        });

        return gcTrends;
    }
}
