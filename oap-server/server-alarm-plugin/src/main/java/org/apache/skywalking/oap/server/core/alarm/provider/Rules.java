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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.alarm.provider.dingtalk.DingtalkSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.discord.DiscordSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.feishu.FeishuSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.grpc.GRPCAlarmSetting;
import org.apache.skywalking.oap.server.core.alarm.provider.pagerduty.PagerDutySettings;
import org.apache.skywalking.oap.server.core.alarm.provider.slack.SlackSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.webhook.WebhookSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.wechat.WechatSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.welink.WeLinkSettings;

@Setter
@Getter
@ToString
public class Rules {
    private List<AlarmRule> rules;
    private Map<String, WebhookSettings> webhookSettingsMap;
    private Map<String, GRPCAlarmSetting> grpcAlarmSettingMap;
    private Map<String, SlackSettings> slackSettingsMap;
    private Map<String, WechatSettings> wechatSettingsMap;
    private Map<String, DingtalkSettings> dingtalkSettingsMap;
    private Map<String, FeishuSettings> feishuSettingsMap;
    private Map<String, WeLinkSettings> weLinkSettingsMap;
    private Map<String, PagerDutySettings> pagerDutySettingsMap;
    private Map<String, DiscordSettings> discordSettingsMap;

    public Rules() {
        this.rules = new ArrayList<>();
        this.webhookSettingsMap = new HashMap<>();
        this.grpcAlarmSettingMap = new HashMap<>();
        this.slackSettingsMap = new HashMap<>();
        this.wechatSettingsMap = new HashMap<>();
        this.dingtalkSettingsMap = new HashMap<>();
        this.feishuSettingsMap = new HashMap<>();
        this.weLinkSettingsMap = new HashMap<>();
        this.pagerDutySettingsMap = new HashMap<>();
        this.discordSettingsMap = new HashMap<>();
    }
}
