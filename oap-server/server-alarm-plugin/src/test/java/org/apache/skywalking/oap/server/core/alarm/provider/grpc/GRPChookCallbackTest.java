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

import java.util.Arrays;
import java.util.List;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.Rules;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GRPChookCallbackTest {

    private GRPCCallback grpcCallback;

    private AlarmRulesWatcher alarmRulesWatcher;

    private List<AlarmMessage> alarmMessageList;

    @BeforeEach
    public void init() throws Exception {
        Rules rules = new Rules();
        GRPCAlarmSetting setting1 = new GRPCAlarmSetting("setting1", AlarmHooksType.gRPC, true);
        setting1.setTargetHost("127.0.0.1");
        setting1.setTargetPort(9888);
        GRPCAlarmSetting setting2 = new GRPCAlarmSetting("setting2", AlarmHooksType.gRPC, false);
        setting2.setTargetHost("127.0.0.1");
        setting2.setTargetPort(9888);
        rules.getGrpcAlarmSettingMap().put(setting1.getFormattedName(), setting1);
        rules.getGrpcAlarmSettingMap().put(setting2.getFormattedName(), setting2);

        alarmRulesWatcher = new AlarmRulesWatcher(rules, null);
        grpcCallback = new GRPCCallback(alarmRulesWatcher);
        mockAlarmMessage(setting1.getFormattedName(), setting2.getFormattedName());
    }

    @Test
    public void doAlarm() {
        grpcCallback.doAlarm(alarmMessageList);
    }

    @Test
    public void testGauchoSettingClean() {
        Rules rules = new Rules();
        GRPCAlarmSetting setting1 = new GRPCAlarmSetting("setting1111111", AlarmHooksType.gRPC, true);
        GRPCAlarmSetting setting2 = new GRPCAlarmSetting("setting2222222", AlarmHooksType.gRPC, true);
        rules.getGrpcAlarmSettingMap().put(setting1.getFormattedName(), setting1);
        rules.getGrpcAlarmSettingMap().put(setting2.getFormattedName(), setting2);
        alarmRulesWatcher = new AlarmRulesWatcher(rules, null);
        grpcCallback = new GRPCCallback(alarmRulesWatcher);
        grpcCallback.doAlarm(alarmMessageList);
    }

    private void mockAlarmMessage(String hook1, String hook2) {
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setId0("1");
        alarmMessage.setId1("2");
        alarmMessage.setScope(Scope.Service.name());
        alarmMessage.setName("mock alarm message");
        alarmMessage.setAlarmMessage("message");
        alarmMessage.setRuleName("mock_rule");
        alarmMessage.setStartTime(System.currentTimeMillis());
        alarmMessage.setTags(Arrays.asList(new Tag("key", "value")));
        alarmMessage.getHooks().add(hook1);
        AlarmMessage alarmMessage2 = new AlarmMessage();
        alarmMessage2.setId0("21");
        alarmMessage2.setId1("22");
        alarmMessage2.setScope(Scope.Service.name());
        alarmMessage2.setName("mock alarm message2");
        alarmMessage2.setAlarmMessage("message2");
        alarmMessage2.setRuleName("mock_rule2");
        alarmMessage2.setStartTime(System.currentTimeMillis());
        alarmMessage2.setTags(Arrays.asList(new Tag("key2", "value2")));
        alarmMessage2.getHooks().add(hook1);
        alarmMessageList = Lists.newArrayList(alarmMessage, alarmMessage2);
    }
}
