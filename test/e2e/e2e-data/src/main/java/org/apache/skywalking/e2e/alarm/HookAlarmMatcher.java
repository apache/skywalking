/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e.alarm;

import lombok.Data;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

@Data
public class HookAlarmMatcher extends AbstractMatcher<HookAlarmMatcher> {
    private String scopeId;
    private String scope;
    private String name;
    private String id0;
    private String id1;
    private String ruleName;
    private String alarmMessage;
    private String startTime;

    @Override
    public void verify(HookAlarmMatcher hookAlarmMatcher) {
        doVerify(this.scopeId, hookAlarmMatcher.getScopeId());
        doVerify(this.scope, hookAlarmMatcher.getScope());
        doVerify(this.name, hookAlarmMatcher.getName());
        doVerify(this.id0, hookAlarmMatcher.getId0());
        doVerify(this.id1, hookAlarmMatcher.getId1());
        doVerify(this.ruleName, hookAlarmMatcher.getRuleName());
        doVerify(this.alarmMessage, hookAlarmMatcher.getAlarmMessage());
        doVerify(this.startTime, hookAlarmMatcher.getStartTime());
    }
}
