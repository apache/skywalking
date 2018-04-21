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

import java.util.Collection;
import java.util.List;
import org.apache.skywalking.apm.collector.storage.base.dao.DAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.ui.common.Node;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceMetric;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;

/**
 * Interface to be implemented for execute database query operation
 * from {@link org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable#TABLE}.
 *
 * @author peng-yongsheng
 * @see org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable
 * @see org.apache.skywalking.apm.collector.storage.StorageModule
 */
public interface IServiceMetricUIDAO extends DAO {

    /**
     * Service Response Time Trend describes the trend of Service metric in the given duration
     * , which represents by the DurationPoint list in the `step` Unit.
     *
     * <p>SQL as: select TRANSACTION_DURATION_SUM / TRANSACTION_CALLS from SERVICE_METRIC
     * where ID in (${durationPoints})
     *
     * <p>rule of ID generation is "${durationPoint}_${serviceId}_${MetricSource.Callee}"
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param serviceId query condition
     * @param step the step which represent time formats
     * @param durationPoints the time points in the time span
     * @return every duration points average response time metrics.
     */
    List<Integer> getServiceResponseTimeTrend(int serviceId, Step step, List<DurationPoint> durationPoints);

    /**
     * Service TPS Trend describes the trend of Service metric in the given duration
     * , which represents by the DurationPoint list in the `step` Unit.
     *
     * <p>SQL as: select TRANSACTION_CALLS / ${durationPoint#secondsBetween} from SERVICE_METRIC
     * where ID in (${durationPoints})
     *
     * <p>rule of ID generation is "${durationPoint}_${serviceId}_${MetricSource.Callee}"
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param serviceId query condition
     * @param step the step which represent time formats
     * @param durationPoints the time points in the time span
     * @return every duration points average throughout metrics.
     */
    List<Integer> getServiceTPSTrend(int serviceId, Step step, List<DurationPoint> durationPoints);

    /**
     * Service SLA Trend describes the trend of Service SLA metrics in the given duration
     * , which represents by the DurationPoint list in the `step` Unit.
     *
     * <p>SQL as: select (( TRANSACTION_CALLS - TRANSACTION_ERROR_CALLS ) * 10000) / TRANSACTION_CALLS
     * from SERVICE_METRIC where ID in (${durationPoints})
     *
     * <p>rule of ID generation is "${durationPoint}_${serviceId}_${MetricSource.Callee}"
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param serviceId query condition
     * @param step the step which represent time formats
     * @param durationPoints the time points in the time span
     * @return every duration points service SLA metrics.
     */
    List<Integer> getServiceSLATrend(int serviceId, Step step, List<DurationPoint> durationPoints);

    /**
     * <p>SQL as: select SERVICE_ID, sum(TRANSACTION_CALLS), sum(TRANSACTION_ERROR_CALLS)
     * from SERVICE_METRIC
     * where TIME_BUCKET ge ${startTimeBucket} and TIME_BUCKET le ${endTimeBucket}
     * and SOURCE_VALUE = ${metricSource} and SERVICE_ID in (${serviceIds})
     * group by SERVICE_ID
     *
     * <p>The SLA formula is "( TRANSACTION_CALLS - TRANSACTION_ERROR_CALLS ) * 10000 ) / TRANSACTION_CALLS"
     *
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param step the step which represent time formats
     * @param startTimeBucket start time bucket
     * @param endTimeBucket end time bucket
     * @param metricSource source of this metric, server side or client side
     * @param serviceIds query condition
     * @return not nullable result list
     */
    List<Node> getServicesMetric(Step step, long startTimeBucket, long endTimeBucket,
        MetricSource metricSource, Collection<Integer> serviceIds);

    /**
     * Returns the top n slow services that collected between start time bucket
     * and end time bucket.
     *
     * <p>SQL as: select SERVICE_ID, TRANSACTION_CALLS, TRANSACTION_AVERAGE_DURATION
     * from SERVICE_METRIC where ID in (${durationPoints})
     * where TIME_BUCKET ge ${startTimeBucket} and TIME_BUCKET le ${endTimeBucket}
     * and SOURCE_VALUE = ${metricSource}
     * and APPLICATION_ID = ${applicationId}
     * order by TRANSACTION_AVERAGE_DURATION desc
     * limit ${topN}
     *
     * <p>Use {@link org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder#build(Step, String)}
     * to generate table name which mixed with step name.
     *
     * @param applicationId query condition
     * @param step the step which represent time formats
     * @param startTimeBucket start time bucket
     * @param endTimeBucket end time bucket
     * @param topN how many rows should return
     * @param metricSource source of this metric, server side or client side
     * @return not nullable result list
     */
    List<ServiceMetric> getSlowService(int applicationId, Step step, long startTimeBucket, long endTimeBucket,
        Integer topN, MetricSource metricSource);
}
