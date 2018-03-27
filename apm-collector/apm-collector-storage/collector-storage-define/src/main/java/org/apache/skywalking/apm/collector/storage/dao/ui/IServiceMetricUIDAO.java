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
 * @author peng-yongsheng
 */
public interface IServiceMetricUIDAO extends DAO {
    List<Integer> getServiceResponseTimeTrend(int serviceId, Step step, List<DurationPoint> durationPoints);

    List<Integer> getServiceTPSTrend(int serviceId, Step step, List<DurationPoint> durationPoints);

    List<Integer> getServiceSLATrend(int serviceId, Step step, List<DurationPoint> durationPoints);

    List<Node> getServicesMetric(Step step, long startTime, long endTime,
        MetricSource metricSource, Collection<Integer> serviceIds);

    List<ServiceMetric> getSlowService(int applicationId, Step step, long startTimeBucket, long endTimeBucket,
        Integer topN, MetricSource metricSource);
}
