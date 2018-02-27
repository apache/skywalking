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
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmList;

/**
 * @author peng-yongsheng
 */
public class ApplicationMetricAlarmToListNodeProcessor implements NodeProcessor<ApplicationAlarm, ApplicationAlarmList> {

    @Override public int id() {
        return AlarmWorkerIdDefine.APPLICATION_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID;
    }

    @Override public void process(ApplicationAlarm applicationAlarm, Next<ApplicationAlarmList> next) {
        String metricId = applicationAlarm.getSourceValue()
            + Const.ID_SPLIT + applicationAlarm.getAlarmType()
            + Const.ID_SPLIT + applicationAlarm.getApplicationId();

        String id = applicationAlarm.getLastTimeBucket() + Const.ID_SPLIT + metricId;

        ApplicationAlarmList applicationAlarmList = new ApplicationAlarmList();
        applicationAlarmList.setId(id);
        applicationAlarmList.setMetricId(metricId);
        applicationAlarmList.setApplicationId(applicationAlarm.getApplicationId());
        applicationAlarmList.setSourceValue(applicationAlarm.getSourceValue());
        applicationAlarmList.setAlarmType(applicationAlarm.getAlarmType());
        applicationAlarmList.setTimeBucket(applicationAlarm.getLastTimeBucket());
        applicationAlarmList.setAlarmContent(applicationAlarm.getAlarmContent());
        next.execute(applicationAlarmList);
    }
}
