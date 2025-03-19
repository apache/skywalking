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
import org.apache.skywalking.oap.server.core.alarm.provider.webhook.WebhookSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.wechat.WechatSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.welink.WeLinkSettings;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RulesReaderTest {
    @BeforeEach
    public void setUp() {
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "service_percent", "testColumn", Column.ValueDataType.COMMON_VALUE, 0, Scope.Service.getScopeId());
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "endpoint_percent", "testColumn", Column.ValueDataType.COMMON_VALUE, 0, Scope.Endpoint.getScopeId());
    }

    @Test
    public void testReadRules() {
        RulesReader reader = new RulesReader(this.getClass()
                .getClassLoader()
                .getResourceAsStream("alarm-settings.yml"), null);
        Rules rules = reader.readRules();

        List<AlarmRule> ruleList = rules.getRules();
        Assertions.assertEquals(5, ruleList.size());
        Assertions.assertEquals("sum(service_percent < 85) >= 4", ruleList.get(1).getExpression());
        Assertions.assertEquals("endpoint_percent_rule", ruleList.get(0).getAlarmRuleName());
        Assertions.assertEquals(0, ruleList.get(0).getIncludeNames().size());
        Assertions.assertEquals(0, ruleList.get(0).getExcludeNames().size());
        Assertions.assertEquals("Successful rate of endpoint {name} is lower than 75%", ruleList.get(0).getMessage());

        Assertions.assertEquals("service_b", ruleList.get(1).getIncludeNames().get(1));
        Assertions.assertEquals("service_c", ruleList.get(1).getExcludeNames().get(0));
        Assertions.assertEquals("Alarm caused by Rule service_percent_rule", ruleList.get(1).getMessage());

        //endpoint_percent_rule's hooks
        Assertions.assertEquals(8, ruleList.get(0).getHooks().size());
        //endpoint_percent_more_rule's hooks
        Assertions.assertEquals(2, ruleList.get(2).getHooks().size());

        WebhookSettings rulesWebhooks = rules.getWebhookSettingsMap().get(AlarmHooksType.webhook.name() + ".default");
        Assertions.assertEquals(2, rulesWebhooks.getUrls().size());
        Assertions.assertEquals("http://127.0.0.1/go-wechat/", rulesWebhooks.getUrls().get(1));
        WebhookSettings rulesWebhooks2 = rules.getWebhookSettingsMap().get(AlarmHooksType.webhook.name() + ".custom1");
        Assertions.assertEquals(2, rulesWebhooks2.getHeaders().size());
        Assertions.assertEquals("Bearer bearer_token", rulesWebhooks2.getHeaders().get("Authorization"));
        Assertions.assertEquals("arbitrary-additional-http-headers", rulesWebhooks2.getHeaders().get("x-company-header"));

        GRPCAlarmSetting grpcAlarmSetting = rules.getGrpcAlarmSettingMap().get(AlarmHooksType.gRPC.name() + ".default");
        assertNotNull(grpcAlarmSetting);
        assertThat(grpcAlarmSetting.getTargetHost()).isEqualTo("127.0.0.1");
        assertThat(grpcAlarmSetting.getTargetPort()).isEqualTo(9888);

        SlackSettings slackSettings = rules.getSlackSettingsMap().get(AlarmHooksType.slack.name() + ".default");
        assertNotNull(slackSettings);
        assertThat(slackSettings.getWebhooks().size()).isEqualTo(1);
        assertThat(slackSettings.getWebhooks().get(0)).isEqualTo("https://hooks.slack.com/services/x/y/zssss");
        assertThat(slackSettings.getTextTemplate()).isInstanceOfAny(String.class);

        WechatSettings wechatSettings = rules.getWechatSettingsMap().get(AlarmHooksType.wechat.name() + ".default");
        assertNotNull(wechatSettings);
        assertThat(wechatSettings.getWebhooks().size()).isEqualTo(1);
        assertThat(wechatSettings.getWebhooks().get(0)).isEqualTo("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=dummy_key");
        assertThat(slackSettings.getTextTemplate()).isInstanceOfAny(String.class);

        DingtalkSettings dingtalkSettings = rules.getDingtalkSettingsMap().get(AlarmHooksType.dingtalk.name() + ".default");
        assertThat(dingtalkSettings.getTextTemplate()).isInstanceOfAny(String.class);
        List<DingtalkSettings.WebHookUrl> webHookUrls = dingtalkSettings.getWebhooks();
        assertThat(webHookUrls.size()).isEqualTo(2);
        assertThat(webHookUrls.get(0).getUrl()).isEqualTo("https://oapi.dingtalk.com/robot/send?access_token=dummy_token");
        assertThat(webHookUrls.get(0).getSecret()).isEqualTo("dummysecret");
        assertThat(webHookUrls.get(1).getUrl()).isEqualTo("https://oapi.dingtalk.com/robot/send?access_token=dummy_token2");
        assertNull(webHookUrls.get(1).getSecret());

        FeishuSettings feishuSettings = rules.getFeishuSettingsMap().get(AlarmHooksType.feishu.name() + ".default");
        assertThat(feishuSettings.getTextTemplate()).isInstanceOfAny(String.class);
        List<FeishuSettings.WebHookUrl> feishuSettingsWebhooks = feishuSettings.getWebhooks();
        assertThat(feishuSettingsWebhooks.size()).isEqualTo(2);
        assertThat(feishuSettingsWebhooks.get(0).getUrl()).isEqualTo("https://open.feishu.cn/open-apis/bot/v2/hook/dummy_token");
        assertThat(feishuSettingsWebhooks.get(0).getSecret()).isEqualTo("dummysecret");
        assertThat(feishuSettingsWebhooks.get(1).getUrl()).isEqualTo("https://open.feishu.cn/open-apis/bot/v2/hook/dummy_token2");
        assertNull(feishuSettingsWebhooks.get(1).getSecret());

        PagerDutySettings pagerDutySettings = rules.getPagerDutySettingsMap().get(AlarmHooksType.pagerduty.name() + ".default");
        assertEquals("dummy_text_template", pagerDutySettings.getTextTemplate());
        List<String> pagerDutyIntegrationKeys = pagerDutySettings.getIntegrationKeys();
        assertEquals(2, pagerDutyIntegrationKeys.size());
        assertEquals("dummy_key", pagerDutyIntegrationKeys.get(0));
        assertEquals("dummy_key2", pagerDutyIntegrationKeys.get(1));

        WeLinkSettings weLinkSettings = rules.getWeLinkSettingsMap().get(AlarmHooksType.welink.name() + ".default");
        assertThat(weLinkSettings.getTextTemplate()).isInstanceOfAny(String.class);
        List<WeLinkSettings.WebHookUrl> weiWebHookUrls = weLinkSettings.getWebhooks();
        assertThat(weiWebHookUrls.size()).isEqualTo(1);
        assertThat(weiWebHookUrls.get(0).getAccessTokenUrl()).isEqualTo("https://open.welink.huaweicloud.com/api/auth/v2/tickets");
        assertThat(weiWebHookUrls.get(0).getMessageUrl()).isEqualTo("https://open.welink.huaweicloud.com/api/welinkim/v1/im-service/chat/group-chat");
        assertThat(weiWebHookUrls.get(0).getClientId()).isEqualTo("dummy_client_id");
        assertThat(weiWebHookUrls.get(0).getClientSecret()).isEqualTo("dummy_secret_key");
        assertThat(weiWebHookUrls.get(0).getRobotName()).isEqualTo("robot");
        assertThat(weiWebHookUrls.get(0).getGroupIds()).isEqualTo("dummy_group_id");
    }
}
