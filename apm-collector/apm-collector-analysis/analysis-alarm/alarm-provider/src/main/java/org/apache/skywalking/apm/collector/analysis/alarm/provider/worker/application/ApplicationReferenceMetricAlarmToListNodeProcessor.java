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
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarmList;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceMetricAlarmToListNodeProcessor implements NodeProcessor<ApplicationReferenceAlarm, ApplicationReferenceAlarmList> {

    @Override public int id() {
        return AlarmWorkerIdDefine.APPLICATION_REFERENCE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID;
    }

    @Override
    public void process(ApplicationReferenceAlarm applicationReferenceAlarm, Next<ApplicationReferenceAlarmList> next) {
        String id = applicationReferenceAlarm.getLastTimeBucket() + Const.ID_SPLIT + applicationReferenceAlarm.getSourceValue()
            + Const.ID_SPLIT + applicationReferenceAlarm.getAlarmType()
            + Const.ID_SPLIT + applicationReferenceAlarm.getFrontApplicationId()
            + Const.ID_SPLIT + applicationReferenceAlarm.getBehindApplicationId();

        ApplicationReferenceAlarmList applicationReferenceAlarmList = new ApplicationReferenceAlarmList();
        applicationReferenceAlarmList.setId(id);
        applicationReferenceAlarmList.setFrontApplicationId(applicationReferenceAlarm.getFrontApplicationId());
        applicationReferenceAlarmList.setBehindApplicationId(applicationReferenceAlarm.getBehindApplicationId());
        applicationReferenceAlarmList.setSourceValue(applicationReferenceAlarm.getSourceValue());
        applicationReferenceAlarmList.setAlarmType(applicationReferenceAlarm.getAlarmType());
        applicationReferenceAlarmList.setTimeBucket(applicationReferenceAlarm.getLastTimeBucket());
        applicationReferenceAlarmList.setAlarmContent(applicationReferenceAlarm.getAlarmContent());
        next.execute(applicationReferenceAlarmList);
    }
}
