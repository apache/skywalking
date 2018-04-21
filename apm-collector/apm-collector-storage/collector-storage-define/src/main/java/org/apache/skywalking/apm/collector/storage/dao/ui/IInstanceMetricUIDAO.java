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

package org.apache.skywalking.apm.collector.storage.dao.ui;

import java.util.List;
import org.apache.skywalking.apm.collector.storage.base.dao.DAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;

/**
 * Interface to be implemented for execute database query operation
 * from {@link org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface IInstanceMetricUIDAO extends DAO {

    /**
     * Returns the top n instance throughput between start time bucket
     * and end time bucket.
     *
     * <p>SQL as: select APPLICATION_ID, sum(TRANSACTION_CALLS) / ${secondBetween} as tps
     * from INSTANCE_METRIC
     * where TIME_BUCKET ge ${startTimeBucket} and TIME_BUCKET le ${endTimeBucket}
     * and SOURCE_VALUE = ${metricSource}
     * and APPLICATION_ID = ${applicationId}
     * group by INSTANCE_ID
     * order by tps desc
     *
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     * <p>Note: ${applicationId} may not be given
     *
     * @param applicationId owner of instances
     * @param step the step which represent time formats
     * @param startTimeBucket start time bucket
     * @param endTimeBucket end time bucket
     * @param secondBetween the seconds between start time bucket and end time bucket
     * @param topN how many rows should return
     * @param metricSource source of this metric, server side or client side
     * @return not nullable result list
     */
    List<AppServerInfo> getServerThroughput(int applicationId, Step step, long startTimeBucket, long endTimeBucket,
        int secondBetween, int topN, MetricSource metricSource);

    /**
     * Server TPS Trend describes the trend of instance throughout in the given duration,
     * which represents by the DurationPoint list in the `step` Unit.
     *
     * <p>SQL as: select TRANSACTION_CALLS from INSTANCE_METRIC where ID in (durationPoints),
     * rule of ID generation is "${durationPoint}_${instanceId}_${MetricSource.Callee}".
     *
     * <p>The formula is "TRANSACTION_CALLS * durationPoint#secondsBetween"
     *
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param instanceId which instance should be query
     * @param step the step which represent time formats
     * @param durationPoints the time points in the time span
     * @return every duration points average instance throughput metrics.
     */
    List<Integer> getServerTPSTrend(int instanceId, Step step, List<DurationPoint> durationPoints);

    /**
     * Response time Trend describes the trend of instance average response time in the given duration,
     * which represents by the DurationPoint list in the `step` Unit.
     *
     * <p>SQL as: select TRANSACTION_DURATION_SUM / TRANSACTION_CALLS from INSTANCE_METRIC where ID in (durationPoints),
     * rule of ID generation is "${durationPoint}_${instanceId}_${MetricSource.Callee}".
     *
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param instanceId which instance should be query
     * @param step the step which represent time formats
     * @param durationPoints the time points in the time span
     * @return every duration points average instance response time metrics.
     */
    List<Integer> getResponseTimeTrend(int instanceId, Step step, List<DurationPoint> durationPoints);
}
