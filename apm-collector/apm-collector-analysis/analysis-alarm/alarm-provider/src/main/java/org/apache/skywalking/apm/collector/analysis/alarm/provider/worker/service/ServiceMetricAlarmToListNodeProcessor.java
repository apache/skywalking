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

package org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.service;

import org.apache.skywalking.apm.collector.analysis.alarm.define.graph.AlarmWorkerIdDefine;
import org.apache.skywalking.apm.collector.core.graph.Next;
import org.apache.skywalking.apm.collector.core.graph.NodeProcessor;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmList;

/**
 * @author peng-yongsheng
 */
public class ServiceMetricAlarmToListNodeProcessor implements NodeProcessor<ServiceAlarm, ServiceAlarmList> {

    @Override public int id() {
        return AlarmWorkerIdDefine.SERVICE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID;
    }

    @Override public void process(ServiceAlarm serviceAlarm, Next<ServiceAlarmList> next) {
        String id = serviceAlarm.getLastTimeBucket() + Const.ID_SPLIT + serviceAlarm.getSourceValue()
            + Const.ID_SPLIT + serviceAlarm.getAlarmType()
            + Const.ID_SPLIT + serviceAlarm.getServiceId();

        ServiceAlarmList serviceAlarmList = new ServiceAlarmList();
        serviceAlarmList.setId(id);
        serviceAlarmList.setApplicationId(serviceAlarm.getApplicationId());
        serviceAlarmList.setInstanceId(serviceAlarm.getInstanceId());
        serviceAlarmList.setServiceId(serviceAlarm.getServiceId());
        serviceAlarmList.setSourceValue(serviceAlarm.getSourceValue());
        serviceAlarmList.setAlarmType(serviceAlarm.getAlarmType());
        serviceAlarmList.setTimeBucket(serviceAlarm.getLastTimeBucket());
        serviceAlarmList.setAlarmContent(serviceAlarm.getAlarmContent());
        next.execute(serviceAlarmList);
    }
}
