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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.alarm.AlarmModule;
import org.apache.skywalking.oap.server.core.alarm.provider.dingtalk.DingtalkSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.discord.DiscordSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.feishu.FeishuSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.grpc.GRPCAlarmSetting;
import org.apache.skywalking.oap.server.core.alarm.provider.pagerduty.PagerDutySettings;
import org.apache.skywalking.oap.server.core.alarm.provider.slack.SlackSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.webhook.WebhookSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.wechat.WechatSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.welink.WeLinkSettings;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

/**
 * Alarm rules' settings can be dynamically updated via configuration center(s), this class is responsible for
 * monitoring the configuration and parsing them into {@link Rules} and {@link #runningContext}.
 *
 * @since 6.5.0
 */
@Slf4j
public class AlarmRulesWatcher extends ConfigChangeWatcher {
    @Getter
    private volatile Map<String, List<RunningRule>> runningContext;
    private volatile Map<AlarmRule, RunningRule> alarmRuleRunningRuleMap;
    @Getter
    private volatile Map<String, Set<String>> exprMetricsMap;
    private volatile Rules rules;
    private volatile String settingsString;

    public AlarmRulesWatcher(Rules defaultRules, ModuleProvider provider) {
        super(AlarmModule.NAME, provider, "alarm-settings");
        this.runningContext = new HashMap<>();
        this.alarmRuleRunningRuleMap = new HashMap<>();
        this.exprMetricsMap = new HashMap<>();
        this.settingsString = null;
        notify(defaultRules);
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (value.getEventType().equals(EventType.DELETE)) {
            settingsString = null;
            notify(new Rules());
        } else {
            settingsString = value.getNewValue();
            RulesReader rulesReader = new RulesReader(new StringReader(value.getNewValue()));
            Rules rules = rulesReader.readRules();
            notify(rules);
        }
    }

    /**
     * Don't invoke before the module finishes start
     */
    public void notify(Rules newRules) {
        Map<AlarmRule, RunningRule> newAlarmRuleRunningRuleMap = new HashMap<>();
        Map<String, List<RunningRule>> newRunningContext = new HashMap<>();
        Map<String, Set<String>> newExprMetricsMap = new HashMap<>();

        newRules.getRules().forEach(rule -> {
            /*
             * If there is already an alarm rule that is the same as the new one, we'll reuse its
             * corresponding runningRule, to keep its history metrics
             */
            RunningRule runningRule = alarmRuleRunningRuleMap.getOrDefault(rule, new RunningRule(rule));

            newAlarmRuleRunningRuleMap.put(rule, runningRule);

            String expression = rule.getExpression();
            newExprMetricsMap.put(expression, rule.getIncludeMetrics());

            List<RunningRule> runningRules = newRunningContext.computeIfAbsent(expression, key -> new ArrayList<>());

            runningRules.add(runningRule);
        });

        this.rules = newRules;
        this.runningContext = newRunningContext;
        this.alarmRuleRunningRuleMap = newAlarmRuleRunningRuleMap;
        this.exprMetricsMap = newExprMetricsMap;
        log.info("Update alarm rules to {}", rules);
    }

    @Override
    public String value() {
        return settingsString;
    }

    public List<AlarmRule> getRules() {
        return this.rules.getRules();
    }

    public Map<String, WebhookSettings> getWebHooks() {
        return this.rules.getWebhookSettingsMap();
    }

    public Map<String, GRPCAlarmSetting> getGrpchookSetting() {
        return this.rules.getGrpcAlarmSettingMap();
    }

    public Map<String, SlackSettings> getSlackSettings() {
        return this.rules.getSlackSettingsMap();
    }

    public Map<String, WechatSettings> getWechatSettings() {
        return this.rules.getWechatSettingsMap();
    }

    public Map<String, DingtalkSettings> getDingtalkSettings() {
        return this.rules.getDingtalkSettingsMap();
    }

    public Map<String, FeishuSettings> getFeishuSettings() {
        return this.rules.getFeishuSettingsMap();
    }

    public Map<String, WeLinkSettings> getWeLinkSettings() {
        return this.rules.getWeLinkSettingsMap();
    }

    public Map<String, PagerDutySettings> getPagerDutySettings() {
        return this.rules.getPagerDutySettingsMap();
    }

    public Map<String, DiscordSettings> getDiscordSettings() {
        return this.rules.getDiscordSettingsMap();
    }
}
