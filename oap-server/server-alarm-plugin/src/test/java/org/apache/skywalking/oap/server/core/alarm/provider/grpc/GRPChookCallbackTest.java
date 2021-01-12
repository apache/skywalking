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

package org.apache.skywalking.oap.server.core.alarm.provider.grpc;

import com.google.common.collect.Lists;
import java.util.List;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.Rules;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.junit.Before;
import org.junit.Test;

public class GRPChookCallbackTest {

    private GRPCCallback grpcCallback;

    private AlarmRulesWatcher alarmRulesWatcher;

    private List<AlarmMessage> alarmMessageList;

    @Before
    public void init() throws Exception {
        GRPCAlarmSetting setting = new GRPCAlarmSetting();
        setting.setTargetHost("127.0.0.1");
        setting.setTargetPort(9888);

        Rules rules = new Rules();
        rules.setGrpchookSetting(setting);

        alarmRulesWatcher = new AlarmRulesWatcher(rules, null);
        grpcCallback = new GRPCCallback(alarmRulesWatcher);
        mockAlarmMessage();
    }

    @Test
    public void doAlarm() {
        grpcCallback.doAlarm(alarmMessageList);
    }

    @Test
    public void testGauchoSettingClean() {
        GRPCAlarmSetting grpcAlarmSetting = new GRPCAlarmSetting();
        Rules rules = new Rules();
        rules.setGrpchookSetting(grpcAlarmSetting);
        alarmRulesWatcher = new AlarmRulesWatcher(rules, null);
        grpcCallback = new GRPCCallback(alarmRulesWatcher);
        grpcCallback.doAlarm(alarmMessageList);
    }

    private void mockAlarmMessage() {
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setId0("1");
        alarmMessage.setId1("2");
        alarmMessage.setScope(Scope.Service.name());
        alarmMessage.setName("mock alarm message");
        alarmMessage.setAlarmMessage("message");
        alarmMessage.setRuleName("mock_rule");
        alarmMessage.setStartTime(System.currentTimeMillis());

        alarmMessageList = Lists.newArrayList(alarmMessage);
    }
}
