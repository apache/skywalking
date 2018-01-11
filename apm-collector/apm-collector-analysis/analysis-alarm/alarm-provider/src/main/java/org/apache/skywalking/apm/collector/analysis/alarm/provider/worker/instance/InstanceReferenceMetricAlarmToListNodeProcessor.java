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

package org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.instance;

import org.apache.skywalking.apm.collector.analysis.alarm.define.graph.AlarmWorkerIdDefine;
import org.apache.skywalking.apm.collector.core.graph.Next;
import org.apache.skywalking.apm.collector.core.graph.NodeProcessor;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmList;

/**
 * @author peng-yongsheng
 */
public class InstanceReferenceMetricAlarmToListNodeProcessor implements NodeProcessor<InstanceReferenceAlarm, InstanceReferenceAlarmList> {

    @Override public int id() {
        return AlarmWorkerIdDefine.INSTANCE_REFERENCE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID;
    }

    @Override
    public void process(InstanceReferenceAlarm instanceReferenceAlarm, Next<InstanceReferenceAlarmList> next) {
        String id = instanceReferenceAlarm.getLastTimeBucket() + Const.ID_SPLIT + instanceReferenceAlarm.getSourceValue()
            + Const.ID_SPLIT + instanceReferenceAlarm.getAlarmType()
            + Const.ID_SPLIT + instanceReferenceAlarm.getFrontInstanceId()
            + Const.ID_SPLIT + instanceReferenceAlarm.getBehindInstanceId();

        InstanceReferenceAlarmList instanceReferenceAlarmList = new InstanceReferenceAlarmList();
        instanceReferenceAlarmList.setId(id);
        instanceReferenceAlarmList.setFrontApplicationId(instanceReferenceAlarm.getFrontApplicationId());
        instanceReferenceAlarmList.setBehindApplicationId(instanceReferenceAlarm.getBehindApplicationId());
        instanceReferenceAlarmList.setFrontInstanceId(instanceReferenceAlarm.getFrontInstanceId());
        instanceReferenceAlarmList.setBehindInstanceId(instanceReferenceAlarm.getBehindInstanceId());
        instanceReferenceAlarmList.setSourceValue(instanceReferenceAlarm.getSourceValue());
        instanceReferenceAlarmList.setAlarmType(instanceReferenceAlarm.getAlarmType());
        instanceReferenceAlarmList.setTimeBucket(instanceReferenceAlarm.getLastTimeBucket());
        instanceReferenceAlarmList.setAlarmContent(instanceReferenceAlarm.getAlarmContent());
        next.execute(instanceReferenceAlarmList);
    }
}
