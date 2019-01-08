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

package org.apache.skywalking.oap.server.core.alarm.provider;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.IndicatorNotify;
import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;

public class NotifyHandler implements IndicatorNotify {
    private final AlarmCore core;
    private final Rules rules;

    public NotifyHandler(Rules rules) {
        this.rules = rules;
        core = new AlarmCore(rules);
    }

    @Override public void notify(MetaInAlarm meta, Indicator indicator) {
        switch (meta.getScope()) {
            case Service:
                break;
            case ServiceInstance:
                break;
            case Endpoint:
                break;
            default:
                return;
        }
        List<RunningRule> runningRules = core.findRunningRule(meta.getIndicatorName());
        if (runningRules == null) {
            return;
        }

        runningRules.forEach(rule -> rule.in(meta, indicator));
    }

    public void init(AlarmCallback... callbacks) {
        List<AlarmCallback> allCallbacks = new ArrayList<>();
        for (AlarmCallback callback : callbacks) {
            allCallbacks.add(callback);
        }
        allCallbacks.add(new WebhookCallback(rules.getWebhooks()));
        core.start(allCallbacks);
    }

}
