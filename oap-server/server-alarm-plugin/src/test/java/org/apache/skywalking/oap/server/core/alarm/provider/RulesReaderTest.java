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
import org.apache.skywalking.oap.server.core.alarm.provider.feishu.FeishuSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.grpc.GRPCAlarmSetting;
import org.apache.skywalking.oap.server.core.alarm.provider.pagerduty.PagerDutySettings;
import org.apache.skywalking.oap.server.core.alarm.provider.slack.SlackSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.wechat.WechatSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RulesReaderTest {
    @Test
    public void testReadRules() {
        RulesReader reader = new RulesReader(this.getClass()
                .getClassLoader()
                .getResourceAsStream("alarm-settings.yml"));
        Rules rules = reader.readRules();

        List<AlarmRule> ruleList = rules.getRules();
        Assertions.assertEquals(3, ruleList.size());
        Assertions.assertEquals("85", ruleList.get(1).getThreshold());
        Assertions.assertEquals("endpoint_percent_rule", ruleList.get(0).getAlarmRuleName());
        Assertions.assertEquals(0, ruleList.get(0).getIncludeNames().size());
        Assertions.assertEquals(0, ruleList.get(0).getExcludeNames().size());
        Assertions.assertEquals("Successful rate of endpoint {name} is lower than 75%", ruleList.get(0).getMessage());

        Assertions.assertEquals("service_b", ruleList.get(1).getIncludeNames().get(1));
        Assertions.assertEquals("service_c", ruleList.get(1).getExcludeNames().get(0));
        Assertions.assertEquals("Alarm caused by Rule service_percent_rule", ruleList.get(1).getMessage());

        List<String> rulesWebhooks = rules.getWebhooks();
        Assertions.assertEquals(2, rulesWebhooks.size());
        Assertions.assertEquals("http://127.0.0.1/go-wechat/", rulesWebhooks.get(1));

        GRPCAlarmSetting grpcAlarmSetting = rules.getGrpchookSetting();
        assertNotNull(grpcAlarmSetting);
        assertThat(grpcAlarmSetting.getTargetHost()).isEqualTo("127.0.0.1");
        assertThat(grpcAlarmSetting.getTargetPort()).isEqualTo(9888);

        SlackSettings slackSettings = rules.getSlacks();
        assertNotNull(slackSettings);
        assertThat(slackSettings.getWebhooks().size()).isEqualTo(1);
        assertThat(slackSettings.getWebhooks().get(0)).isEqualTo("https://hooks.slack.com/services/x/y/zssss");
        assertThat(slackSettings.getTextTemplate()).isInstanceOfAny(String.class);

        WechatSettings wechatSettings = rules.getWecchats();
        assertNotNull(wechatSettings);
        assertThat(wechatSettings.getWebhooks().size()).isEqualTo(1);
        assertThat(wechatSettings.getWebhooks().get(0)).isEqualTo("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=dummy_key");
        assertThat(slackSettings.getTextTemplate()).isInstanceOfAny(String.class);

        List<CompositeAlarmRule> compositeRules = rules.getCompositeRules();
        Assertions.assertEquals(1, compositeRules.size());
        Assertions.assertEquals("endpoint_percent_more_rule && endpoint_percent_rule", compositeRules.get(0).getExpression());

        DingtalkSettings dingtalkSettings = rules.getDingtalks();
        assertThat(dingtalkSettings.getTextTemplate()).isInstanceOfAny(String.class);
        List<DingtalkSettings.WebHookUrl> webHookUrls = dingtalkSettings.getWebhooks();
        assertThat(webHookUrls.size()).isEqualTo(2);
        assertThat(webHookUrls.get(0).getUrl()).isEqualTo("https://oapi.dingtalk.com/robot/send?access_token=dummy_token");
        assertThat(webHookUrls.get(0).getSecret()).isEqualTo("dummysecret");
        assertThat(webHookUrls.get(1).getUrl()).isEqualTo("https://oapi.dingtalk.com/robot/send?access_token=dummy_token2");
        assertNull(webHookUrls.get(1).getSecret());

        FeishuSettings feishuSettings = rules.getFeishus();
        assertThat(feishuSettings.getTextTemplate()).isInstanceOfAny(String.class);
        List<FeishuSettings.WebHookUrl> feishuSettingsWebhooks = feishuSettings.getWebhooks();
        assertThat(feishuSettingsWebhooks.size()).isEqualTo(2);
        assertThat(feishuSettingsWebhooks.get(0).getUrl()).isEqualTo("https://open.feishu.cn/open-apis/bot/v2/hook/dummy_token");
        assertThat(feishuSettingsWebhooks.get(0).getSecret()).isEqualTo("dummysecret");
        assertThat(feishuSettingsWebhooks.get(1).getUrl()).isEqualTo("https://open.feishu.cn/open-apis/bot/v2/hook/dummy_token2");
        assertNull(feishuSettingsWebhooks.get(1).getSecret());

        PagerDutySettings pagerDutySettings = rules.getPagerDutySettings();
        assertEquals("dummy_text_template", pagerDutySettings.getTextTemplate());
        List<String> pagerDutyIntegrationKeys = pagerDutySettings.getIntegrationKeys();
        assertEquals(2, pagerDutyIntegrationKeys.size());
        assertEquals("dummy_key", pagerDutyIntegrationKeys.get(0));
        assertEquals("dummy_key2", pagerDutyIntegrationKeys.get(1));
    }
}
