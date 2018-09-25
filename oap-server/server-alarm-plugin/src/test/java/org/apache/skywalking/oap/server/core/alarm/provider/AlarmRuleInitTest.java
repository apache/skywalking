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

import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class AlarmRuleInitTest {
    @Test
    public void testInit() {
        RulesReader reader = new RulesReader(this.getClass().getClassLoader()
            .getResourceAsStream("alarm-settings.yml"));
        Rules rules = reader.readRules();

        List<AlarmRule> ruleList = rules.getRules();
        Assert.assertEquals(2, ruleList.size());
        Assert.assertEquals("85", ruleList.get(1).getThreshold());
        Assert.assertEquals("endpoint_percent_rule", ruleList.get(0).getAlarmRuleName());
        Assert.assertEquals(0, ruleList.get(0).getIncludeNames().size());
        Assert.assertEquals("Successful rate of endpoint {name} is lower than 75%", ruleList.get(0).getMessage());

        Assert.assertEquals("service_b", ruleList.get(1).getIncludeNames().get(1));
        Assert.assertEquals("Alarm caused by Rule service_percent_rule", ruleList.get(1).getMessage());

        List<String> rulesWebhooks = rules.getWebhooks();
        Assert.assertEquals(2, rulesWebhooks.size());
        Assert.assertEquals("http://127.0.0.1/go-wechat/", rulesWebhooks.get(1));

    }
}
