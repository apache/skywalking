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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.DurationWithinTTL;

public class ShardingMetricsQueryDAO extends JDBCMetricsQueryDAO {

    public ShardingMetricsQueryDAO(JDBCHikariCPClient h2Client) {
        super(h2Client);
    }

    @Override
    public long readMetricsValue(final MetricsCondition condition,
                                 String valueColumnName,
                                 final Duration duration) throws IOException {
        return super.readMetricsValue(condition, valueColumnName, DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration));
    }

    @Override
    public MetricsValues readMetricsValues(final MetricsCondition condition,
                                           final String valueColumnName,
                                           final Duration duration) throws IOException {

        MetricsValues result = super.readMetricsValues(condition, valueColumnName,
                                                       DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration));
        MetricsValues values = new MetricsValues();
        values.setValues(Util.sortValues(result.getValues(), getOriginIds(condition, duration),
                                         ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName())));
        return values;

    }

    @Override
    public List<MetricsValues> readLabeledMetricsValues(final MetricsCondition condition,
                                                        final String valueColumnName,
                                                        final List<String> labels,
                                                        final Duration duration) throws IOException {
        List<MetricsValues> result = super.readLabeledMetricsValues(condition, valueColumnName, labels,
                                                                    DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration));

        return Util.sortValues(result, getOriginIds(condition, duration),
                               ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName()));
    }

    @Override
    public HeatMap readHeatMap(final MetricsCondition condition,
                               final String valueColumnName,
                               final Duration duration) throws IOException {
        HeatMap result = super.readHeatMap(condition, valueColumnName,
                                           DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration));
        result.fixMissingColumns(getOriginIds(condition, duration),
                                 ValueColumnMetadata.INSTANCE.getDefaultValue(condition.getName()));
        return result;
    }

    @Override
    protected void buildShardingCondition(StringBuilder sql, List<Object> parameters, String entityId) {
        sql.append(" and ");
        sql.append(Metrics.ENTITY_ID + " = ?");
        parameters.add(entityId);
    }

    private List<String> getOriginIds(final MetricsCondition condition, final Duration duration) {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        List<String> ids = new ArrayList<>(pointOfTimes.size());
        final String entityId = condition.getEntity().buildId();
        pointOfTimes.forEach(pointOfTime -> {
            ids.add(pointOfTime.id(entityId));
        });

        return ids;
    }
}
