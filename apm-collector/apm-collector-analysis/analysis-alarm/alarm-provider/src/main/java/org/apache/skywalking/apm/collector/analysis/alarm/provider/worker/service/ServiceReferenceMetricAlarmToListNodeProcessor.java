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
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmList;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceMetricAlarmToListNodeProcessor implements NodeProcessor<ServiceReferenceAlarm, ServiceReferenceAlarmList> {

    @Override public int id() {
        return AlarmWorkerIdDefine.SERVICE_REFERENCE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID;
    }

    @Override public void process(ServiceReferenceAlarm serviceReferenceAlarm, Next<ServiceReferenceAlarmList> next) {
        String id = serviceReferenceAlarm.getLastTimeBucket() + Const.ID_SPLIT + serviceReferenceAlarm.getSourceValue()
            + Const.ID_SPLIT + serviceReferenceAlarm.getAlarmType()
            + Const.ID_SPLIT + serviceReferenceAlarm.getFrontServiceId()
            + Const.ID_SPLIT + serviceReferenceAlarm.getBehindServiceId();

        ServiceReferenceAlarmList serviceReferenceAlarmList = new ServiceReferenceAlarmList();
        serviceReferenceAlarmList.setId(id);
        serviceReferenceAlarmList.setFrontApplicationId(serviceReferenceAlarm.getFrontApplicationId());
        serviceReferenceAlarmList.setBehindApplicationId(serviceReferenceAlarm.getBehindApplicationId());
        serviceReferenceAlarmList.setFrontInstanceId(serviceReferenceAlarm.getFrontInstanceId());
        serviceReferenceAlarmList.setBehindInstanceId(serviceReferenceAlarm.getBehindInstanceId());
        serviceReferenceAlarmList.setFrontServiceId(serviceReferenceAlarm.getFrontServiceId());
        serviceReferenceAlarmList.setBehindServiceId(serviceReferenceAlarm.getBehindServiceId());
        serviceReferenceAlarmList.setSourceValue(serviceReferenceAlarm.getSourceValue());
        serviceReferenceAlarmList.setAlarmType(serviceReferenceAlarm.getAlarmType());
        serviceReferenceAlarmList.setTimeBucket(serviceReferenceAlarm.getLastTimeBucket());
        serviceReferenceAlarmList.setAlarmContent(serviceReferenceAlarm.getAlarmContent());
        next.execute(serviceReferenceAlarmList);
    }
}
