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

package org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.application;

import org.apache.skywalking.apm.collector.analysis.alarm.define.graph.AlarmWorkerIdDefine;
import org.apache.skywalking.apm.collector.core.graph.Next;
import org.apache.skywalking.apm.collector.core.graph.NodeProcessor;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmList;

/**
 * @author peng-yongsheng
 */
public class ApplicationMetricAlarmListMonthTransformNode implements NodeProcessor<ApplicationAlarmList, ApplicationAlarmList> {

    @Override public int id() {
        return AlarmWorkerIdDefine.APPLICATION_METRIC_ALARM_LIST_MONTH_TRANSFORM_NODE_ID;
    }

    @Override
    public void process(ApplicationAlarmList applicationAlarmList, Next<ApplicationAlarmList> next) {
        long timeBucket = TimeBucketUtils.INSTANCE.minuteToMonth(applicationAlarmList.getTimeBucket());

        ApplicationAlarmList newApplicationAlarmList = ApplicationMetricAlarmListCopy.copy(applicationAlarmList);
        newApplicationAlarmList.setId(String.valueOf(timeBucket) + Const.ID_SPLIT + applicationAlarmList.getMetricId());
        newApplicationAlarmList.setTimeBucket(timeBucket);
        next.execute(newApplicationAlarmList);
    }
}
