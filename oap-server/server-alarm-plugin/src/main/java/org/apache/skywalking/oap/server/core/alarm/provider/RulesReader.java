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

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.alarm.provider.discord.DiscordSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.pagerduty.PagerDutySettings;
import org.apache.skywalking.oap.server.core.alarm.provider.webhook.WebhookSettings;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.core.alarm.provider.dingtalk.DingtalkSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.feishu.FeishuSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.grpc.GRPCAlarmSetting;
import org.apache.skywalking.oap.server.core.alarm.provider.slack.SlackSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.wechat.WechatSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.welink.WeLinkSettings;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Rule Reader parses the given `alarm-settings.yml` config file, to the target {@link Rules}.
 */
public class RulesReader {
    private Map yamlData;
    private final Set<String> globalHooks = new HashSet<>();

    public RulesReader(InputStream inputStream) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        yamlData = yaml.load(inputStream);
    }

    public RulesReader(Reader io) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        yamlData = yaml.load(io);
    }

    /**
     * Read rule config file to {@link Rules}
     */
    public Rules readRules() {
        Rules rules = new Rules();

        if (Objects.nonNull(yamlData)) {
            // Should read hooks config first.
            readHooksConfig(rules);
            readRulesConfig(rules);
            readCompositeRuleConfig(rules);
        }
        return rules;
    }

    /**
     * Read rule config into {@link AlarmRule}
     */
    private void readRulesConfig(Rules rules) {
        Map rulesData = (Map) yamlData.get("rules");
        if (rulesData == null) {
            return;
        }
        rules.setRules(new ArrayList<>());
        rulesData.forEach((k, v) -> {
            if (((String) k).endsWith("_rule")) {
                AlarmRule alarmRule = new AlarmRule();
                alarmRule.setAlarmRuleName((String) k);
                Map settings = (Map) v;
                Object metricsName = settings.get("metrics-name");
                if (metricsName == null) {
                    throw new IllegalArgumentException("metrics-name can't be null");
                }

                alarmRule.setMetricsName((String) metricsName);
                alarmRule.setIncludeNames((ArrayList) settings.getOrDefault("include-names", new ArrayList(0)));
                alarmRule.setExcludeNames((ArrayList) settings.getOrDefault("exclude-names", new ArrayList(0)));
                alarmRule.setIncludeNamesRegex((String) settings.getOrDefault("include-names-regex", ""));
                alarmRule.setExcludeNamesRegex((String) settings.getOrDefault("exclude-names-regex", ""));
                alarmRule.setIncludeLabels(
                        (ArrayList) settings.getOrDefault("include-labels", new ArrayList(0)));
                alarmRule.setExcludeLabels(
                        (ArrayList) settings.getOrDefault("exclude-labels", new ArrayList(0)));
                alarmRule.setIncludeLabelsRegex((String) settings.getOrDefault("include-labels-regex", ""));
                alarmRule.setExcludeLabelsRegex((String) settings.getOrDefault("exclude-labels-regex", ""));
                alarmRule.setThreshold(settings.get("threshold").toString());
                alarmRule.setOp((String) settings.get("op"));
                alarmRule.setPeriod((Integer) settings.getOrDefault("period", 1));
                alarmRule.setCount((Integer) settings.getOrDefault("count", 1));
                // How many times of checks, the alarm keeps silence after alarm triggered, default as same as period.
                alarmRule.setSilencePeriod((Integer) settings.getOrDefault("silence-period", alarmRule.getPeriod()));
                alarmRule.setOnlyAsCondition((Boolean) settings.getOrDefault("only-as-condition", false));
                alarmRule.setMessage(
                        (String) settings.getOrDefault("message", "Alarm caused by Rule " + alarmRule
                                .getAlarmRuleName()));
                alarmRule.setTags((Map) settings.getOrDefault("tags", new HashMap<String, String>()));
                alarmRule.setHooks(
                    new HashSet<>((ArrayList) settings.getOrDefault("specific-hooks", new ArrayList<>())));
                // If no specific hooks, use global hooks.
                if (alarmRule.getHooks().isEmpty()) {
                    alarmRule.getHooks().addAll(globalHooks);
                }
                rules.getRules().add(alarmRule);
            }
        });
    }

    private void readHooksConfig(Rules rules) {
        Map hooks = (Map) yamlData.getOrDefault("hooks", Collections.EMPTY_MAP);
        if (CollectionUtils.isEmpty(hooks)) {
            return;
        }
        readWebHookConfig(hooks, rules);
        readGrpcConfig(hooks, rules);
        readSlackConfig(hooks, rules);
        readWechatConfig(hooks, rules);
        readDingtalkConfig(hooks, rules);
        readFeishuConfig(hooks, rules);
        readWeLinkConfig(hooks, rules);
        readPagerDutyConfig(hooks, rules);
        readDiscordConfig(hooks, rules);
    }

    /**
     * Read web hook config
     */
    @SuppressWarnings("unchecked")
    private void readWebHookConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.webhooks.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            WebhookSettings settings = new WebhookSettings(
                k.toString(), AlarmHooksType.webhooks, (Boolean) config.getOrDefault("isGlobal", false));

            List<String> urls = (List<String>) config.get("urls");
            if (urls != null) {
                settings.getUrls().addAll(urls);
            }
            rules.getWebhookSettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isGlobal()) {
                this.globalHooks.add(settings.getFormattedName());
            }
        });
    }

    /**
     * Read grpc hook config into {@link GRPCAlarmSetting}
     */
    @SuppressWarnings("unchecked")
    private void readGrpcConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.gRPCHooks.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map config = (Map) v;
            GRPCAlarmSetting setting = new GRPCAlarmSetting(
                k.toString(), AlarmHooksType.gRPCHooks, (Boolean) config.getOrDefault("isGlobal", false));

            Object targetHost = config.get("target_host");
            if (targetHost != null) {
                setting.setTargetHost((String) targetHost);
            }

            Object targetPort = config.get("target_port");
            if (targetPort != null) {
                setting.setTargetPort((Integer) targetPort);
            }

            rules.getGrpcAlarmSettingMap().put(setting.getFormattedName(), setting);

            if (setting.isGlobal()) {
                this.globalHooks.add(setting.getFormattedName());
            }
        });
    }

    /**
     * Read slack hook config into {@link SlackSettings}
     */
    @SuppressWarnings("unchecked")
    private void readSlackConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.slackHooks.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            SlackSettings settings = new SlackSettings(
                k.toString(), AlarmHooksType.slackHooks, (Boolean) config.getOrDefault("isGlobal", false));

            Object textTemplate = config.getOrDefault("textTemplate", "");
            settings.setTextTemplate((String) textTemplate);

            List<String> webhooks = (List<String>) config.get("webhooks");
            if (webhooks != null) {
                settings.getWebhooks().addAll(webhooks);
            }
            rules.getSlackSettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isGlobal()) {
                this.globalHooks.add(settings.getFormattedName());
            }
        });
    }

    /**
     * Read wechat hook config into {@link WechatSettings}
     */
    @SuppressWarnings("unchecked")
    private void readWechatConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.wechatHooks.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            WechatSettings settings = new WechatSettings(
                k.toString(), AlarmHooksType.wechatHooks, (Boolean) config.getOrDefault("isGlobal", false));

            Object textTemplate = config.getOrDefault("textTemplate", "");
            settings.setTextTemplate((String) textTemplate);

            List<String> webhooks = (List<String>) config.get("webhooks");
            if (webhooks != null) {
                settings.getWebhooks().addAll(webhooks);
            }
            rules.getWechatSettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isGlobal()) {
                this.globalHooks.add(settings.getFormattedName());
            }
        });
    }

    /**
     * Read composite rule config into {@link CompositeAlarmRule}
     */
    @SuppressWarnings("unchecked")
    private void readCompositeRuleConfig(Rules rules) {
        Map compositeRulesData = (Map) yamlData.get("composite-rules");
        if (compositeRulesData == null) {
            return;
        }
        compositeRulesData.forEach((k, v) -> {
            String ruleName = (String) k;
            if (ruleName.endsWith("_rule")) {
                Map settings = (Map) v;
                CompositeAlarmRule compositeAlarmRule = new CompositeAlarmRule();
                compositeAlarmRule.setAlarmRuleName(ruleName);
                String expression = (String) settings.get("expression");
                if (expression == null) {
                    throw new IllegalArgumentException("expression can't be null");
                }
                compositeAlarmRule.setExpression(expression);
                compositeAlarmRule.setMessage(
                        (String) settings.getOrDefault("message", "Alarm caused by Rule " + ruleName));
                compositeAlarmRule.setTags((Map) settings.getOrDefault("tags", new HashMap<String, String>(0)));
                compositeAlarmRule.setHooks(
                    new HashSet<>((ArrayList) settings.getOrDefault("specific-hooks", new ArrayList<>())));
                // If no specific hooks, use global hooks.
                if (compositeAlarmRule.getHooks().isEmpty()) {
                    compositeAlarmRule.getHooks().addAll(globalHooks);
                }
                rules.getCompositeRules().add(compositeAlarmRule);
            }
        });
    }

    /**
     * Read dingtalk hook config into {@link DingtalkSettings}
     */
    @SuppressWarnings("unchecked")
    private void readDingtalkConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.dingtalkHooks.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            DingtalkSettings settings = new DingtalkSettings(
                k.toString(), AlarmHooksType.dingtalkHooks, (Boolean) config.getOrDefault("isGlobal", false));

            Object textTemplate = config.getOrDefault("textTemplate", "");
            settings.setTextTemplate((String) textTemplate);

            List<Map<String, Object>> webhooks = (List<Map<String, Object>>) config.get("webhooks");
            if (webhooks != null) {
                webhooks.forEach(webhook -> {
                    Object secret = webhook.getOrDefault("secret", "");
                    Object url = webhook.getOrDefault("url", "");
                    settings.getWebhooks().add(new DingtalkSettings.WebHookUrl((String) secret, (String) url));
                });
            }
            rules.getDingtalkSettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isGlobal()) {
                this.globalHooks.add(settings.getFormattedName());
            }
        });
    }

    /**
     * Read feishu hook config into {@link FeishuSettings}
     */
    @SuppressWarnings("unchecked")
    private void readFeishuConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.feishuHooks.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            FeishuSettings settings = new FeishuSettings(
                k.toString(), AlarmHooksType.feishuHooks, (Boolean) config.getOrDefault("isGlobal", false));

            Object textTemplate = config.getOrDefault("textTemplate", "");
            settings.setTextTemplate((String) textTemplate);

            List<Map<String, Object>> webhooks = (List<Map<String, Object>>) config.get("webhooks");
            if (webhooks != null) {
                webhooks.forEach(webhook -> {
                    Object secret = webhook.getOrDefault("secret", "");
                    Object url = webhook.getOrDefault("url", "");
                    settings.getWebhooks().add(new FeishuSettings.WebHookUrl((String) secret, (String) url));
                });
            }
            rules.getFeishuSettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isGlobal()) {
                this.globalHooks.add(settings.getFormattedName());
            }
        });
    }

    /**
     * Read WeLink hook config into {@link WeLinkSettings}
     */
    @SuppressWarnings("unchecked")
    private void readWeLinkConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.welinkHooks.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            String textTemplate = (String) config.get("textTemplate");
            List<Map<String, String>> webhooks = (List<Map<String, String>>) config.get("webhooks");
            if (StringUtil.isBlank(textTemplate) || CollectionUtils.isEmpty(webhooks)) {
                return;
            }
            List<WeLinkSettings.WebHookUrl> webHookUrls = webhooks.stream().map(
                WeLinkSettings.WebHookUrl::generateFromMap
            ).collect(Collectors.toList());

            WeLinkSettings settings = new WeLinkSettings(
                k.toString(), AlarmHooksType.welinkHooks, (Boolean) config.getOrDefault("isGlobal", false));
            settings.setTextTemplate(textTemplate);
            settings.setWebhooks(webHookUrls);

            rules.getWeLinkSettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isGlobal()) {
                this.globalHooks.add(settings.getFormattedName());
            }
        });
    }

    /**
     * Read PagerDuty hook config into {@link PagerDutySettings}
     */
    @SuppressWarnings("unchecked")
    private void readPagerDutyConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.pagerDutyHooks.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            PagerDutySettings settings = new PagerDutySettings(
                k.toString(), AlarmHooksType.pagerDutyHooks, (Boolean) config.getOrDefault("isGlobal", false));
            Object textTemplate = config.getOrDefault("textTemplate", "");
            settings.setTextTemplate((String) textTemplate);

            List<String> integrationKeys = (List<String>) config.get("integrationKeys");
            if (integrationKeys != null) {
                settings.getIntegrationKeys().addAll(integrationKeys);
            }

            rules.getPagerDutySettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isGlobal()) {
                this.globalHooks.add(settings.getFormattedName());
            }
        });
    }

    /**
     * Read Discord hook config into {@link DiscordSettings}
     */
    @SuppressWarnings("unchecked")
    private void readDiscordConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.discordHooks.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            String textTemplate = (String) config.get("textTemplate");
            List<Map<String, String>> webhooks = (List<Map<String, String>>) config.get("webhooks");
            if (StringUtil.isBlank(textTemplate) || CollectionUtils.isEmpty(webhooks)) {
                return;
            }
            List<DiscordSettings.WebHookUrl> webHookUrls = webhooks.stream().map(
                DiscordSettings.WebHookUrl::generateFromMap
            ).collect(Collectors.toList());

            DiscordSettings settings = new DiscordSettings(
                k.toString(), AlarmHooksType.discordHooks, (Boolean) config.getOrDefault("isGlobal", false));
            settings.setTextTemplate(textTemplate);
            settings.setWebhooks(webHookUrls);

            rules.getDiscordSettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isGlobal()) {
                this.globalHooks.add(settings.getFormattedName());
            }
        });
    }
}
