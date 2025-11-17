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

package org.apache.skywalking.oap.server.core.alarm;

import lombok.Getter;
import lombok.Setter;

/**
 * Alarm message represents the details of each alarm.
 */
@Setter
@Getter
public class AlarmRecoveryMessage extends AlarmMessage {
    private long recoveryTime;

    public AlarmRecoveryMessage(AlarmMessage alarmMessage) {
        this.setScopeId(alarmMessage.getScopeId());
        this.setScope(alarmMessage.getScope());
        this.setName(alarmMessage.getName());
        this.setId0(alarmMessage.getId0());
        this.setId1(alarmMessage.getId1());
        this.setRuleName(alarmMessage.getRuleName());
        this.setAlarmMessage(alarmMessage.getAlarmMessage());
        this.setTags(alarmMessage.getTags());
        this.setStartTime(alarmMessage.getStartTime());
        this.setPeriod(alarmMessage.getPeriod());
        this.setHooks(alarmMessage.getHooks());
        this.setExpression(alarmMessage.getExpression());
        this.setMqeMetricsSnapshot(alarmMessage.getMqeMetricsSnapshot());
        this.setUuid(alarmMessage.getUuid());
        this.setRecoveryTime(System.currentTimeMillis());
    }
}
