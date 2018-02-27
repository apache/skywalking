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
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmList;

/**
 * @author peng-yongsheng
 */
public class InstanceMetricAlarmToListNodeProcessor implements NodeProcessor<InstanceAlarm, InstanceAlarmList> {

    @Override public int id() {
        return AlarmWorkerIdDefine.INSTANCE_METRIC_ALARM_TO_LIST_NODE_PROCESSOR_ID;
    }

    @Override public void process(InstanceAlarm instanceAlarm, Next<InstanceAlarmList> next) {
        String id = instanceAlarm.getLastTimeBucket() + Const.ID_SPLIT + instanceAlarm.getSourceValue()
            + Const.ID_SPLIT + instanceAlarm.getAlarmType()
            + Const.ID_SPLIT + instanceAlarm.getInstanceId();

        InstanceAlarmList instanceAlarmList = new InstanceAlarmList();
        instanceAlarmList.setId(id);
        instanceAlarmList.setApplicationId(instanceAlarm.getApplicationId());
        instanceAlarmList.setInstanceId(instanceAlarm.getInstanceId());
        instanceAlarmList.setSourceValue(instanceAlarm.getSourceValue());
        instanceAlarmList.setAlarmType(instanceAlarm.getAlarmType());
        instanceAlarmList.setTimeBucket(instanceAlarm.getLastTimeBucket());
        instanceAlarmList.setAlarmContent(instanceAlarm.getAlarmContent());
        next.execute(instanceAlarmList);
    }
}
