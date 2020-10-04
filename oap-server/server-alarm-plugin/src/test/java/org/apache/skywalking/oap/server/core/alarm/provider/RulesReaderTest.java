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

import org.apache.skywalking.oap.server.core.alarm.provider.dingtalk.DingtalkSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.grpc.GRPCAlarmSetting;
import org.apache.skywalking.oap.server.core.alarm.provider.slack.SlackSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.wechat.WechatSettings;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class RulesReaderTest {
    @Test
    public void testReadRules() {
        RulesReader reader = new RulesReader(this.getClass()
                .getClassLoader()
                .getResourceAsStream("alarm-settings.yml"));
        Rules rules = reader.readRules();

        List<AlarmRule> ruleList = rules.getRules();
        Assert.assertEquals(3, ruleList.size());
        Assert.assertEquals("85", ruleList.get(1).getThreshold());
        Assert.assertEquals("endpoint_percent_rule", ruleList.get(0).getAlarmRuleName());
        Assert.assertEquals(0, ruleList.get(0).getIncludeNames().size());
        Assert.assertEquals(0, ruleList.get(0).getExcludeNames().size());
        Assert.assertEquals("Successful rate of endpoint {name} is lower than 75%", ruleList.get(0).getMessage());

        Assert.assertEquals("service_b", ruleList.get(1).getIncludeNames().get(1));
        Assert.assertEquals("service_c", ruleList.get(1).getExcludeNames().get(0));
        Assert.assertEquals("Alarm caused by Rule service_percent_rule", ruleList.get(1).getMessage());

        List<String> rulesWebhooks = rules.getWebhooks();
        Assert.assertEquals(2, rulesWebhooks.size());
        Assert.assertEquals("http://127.0.0.1/go-wechat/", rulesWebhooks.get(1));

        GRPCAlarmSetting grpcAlarmSetting = rules.getGrpchookSetting();
        assertNotNull(grpcAlarmSetting);
        assertThat(grpcAlarmSetting.getTargetHost(), is("127.0.0.1"));
        assertThat(grpcAlarmSetting.getTargetPort(), is(9888));

        SlackSettings slackSettings = rules.getSlacks();
        assertNotNull(slackSettings);
        assertThat(slackSettings.getWebhooks().size(), is(1));
        assertThat(slackSettings.getWebhooks().get(0), is("https://hooks.slack.com/services/x/y/zssss"));
        assertThat(slackSettings.getTextTemplate(), any(String.class));

        WechatSettings wechatSettings = rules.getWecchats();
        assertNotNull(wechatSettings);
        assertThat(wechatSettings.getWebhooks().size(), is(1));
        assertThat(wechatSettings.getWebhooks().get(0), is("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=dummy_key"));
        assertThat(slackSettings.getTextTemplate(), any(String.class));

        List<CompositeAlarmRule> compositeRules = rules.getCompositeRules();
        Assert.assertEquals(1, compositeRules.size());
        Assert.assertEquals("endpoint_percent_more_rule && endpoint_percent_rule", compositeRules.get(0).getExpression());

        DingtalkSettings dingtalkSettings = rules.getDingtalks();
        assertThat(dingtalkSettings.getTextTemplate(), any(String.class));
        List<DingtalkSettings.WebHookUrl> webHookUrls = dingtalkSettings.getWebhooks();
        assertThat(webHookUrls.size(), is(2));
        assertThat(webHookUrls.get(0).getUrl(), is("https://oapi.dingtalk.com/robot/send?access_token=dummy_token"));
        assertThat(webHookUrls.get(0).getSecret(), is("dummysecret"));
        assertThat(webHookUrls.get(1).getUrl(), is("https://oapi.dingtalk.com/robot/send?access_token=dummy_token2"));
        assertNull(webHookUrls.get(1).getSecret());
    }
}
