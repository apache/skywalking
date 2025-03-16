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
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.server.core.alarm.provider.discord.DiscordSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.pagerduty.PagerDutySettings;
import org.apache.skywalking.oap.server.core.alarm.provider.webhook.WebhookSettings;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
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
    private final Set<String> defaultHooks = new HashSet<>();
    private final Set<String> allHooks = new HashSet<>();
    private final ModuleManager moduleManager;

    public RulesReader(InputStream inputStream, ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        yamlData = yaml.load(inputStream);
    }

    public RulesReader(Reader io, ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
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
                AlarmRule alarmRule = new AlarmRule(moduleManager);
                alarmRule.setAlarmRuleName((String) k);
                Map settings = (Map) v;
                Object expression = settings.get("expression");
                if (StringUtil.isEmpty((String) expression)) {
                    throw new IllegalArgumentException("expression can't be empty");
                }
                try {
                    alarmRule.setExpression(expression.toString());
                } catch (IllegalExpressionException e) {
                    throw new IllegalArgumentException(e);
                }
                alarmRule.setIncludeNames((ArrayList) settings.getOrDefault("include-names", new ArrayList(0)));
                alarmRule.setExcludeNames((ArrayList) settings.getOrDefault("exclude-names", new ArrayList(0)));
                alarmRule.setIncludeNamesRegex((String) settings.getOrDefault("include-names-regex", ""));
                alarmRule.setExcludeNamesRegex((String) settings.getOrDefault("exclude-names-regex", ""));
                alarmRule.setPeriod((Integer) settings.getOrDefault("period", 1));
                // How many times of checks, the alarm keeps silence after alarm triggered, default as same as period.
                alarmRule.setSilencePeriod((Integer) settings.getOrDefault("silence-period", alarmRule.getPeriod()));
                alarmRule.setMessage(
                        (String) settings.getOrDefault("message", "Alarm caused by Rule " + alarmRule
                                .getAlarmRuleName()));
                alarmRule.setTags((Map) settings.getOrDefault("tags", new HashMap<String, String>()));

                Set<String> specificHooks = new HashSet<>((ArrayList) settings.getOrDefault("hooks", new ArrayList<>()));
                checkSpecificHooks(alarmRule.getAlarmRuleName(), specificHooks);
                alarmRule.setHooks(specificHooks);
                // If no specific hooks, use global hooks.
                if (alarmRule.getHooks().isEmpty()) {
                    alarmRule.getHooks().addAll(defaultHooks);
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
        Map configs = (Map) hooks.get(AlarmHooksType.webhook.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            WebhookSettings settings = new WebhookSettings(
                k.toString(), AlarmHooksType.webhook, (Boolean) config.getOrDefault("is-default", false));

            List<String> urls = (List<String>) config.get("urls");
            if (urls != null) {
                settings.getUrls().addAll(urls);
            }
            Map<String, String> authorizationConf = (Map<String, String>)config.get("authorization");
            if (authorizationConf != null) {
                String type = authorizationConf.get("type");
                String credentials = authorizationConf.get("credentials");
                WebhookSettings.Authorization authorization = WebhookSettings.Authorization.builder().type(type).credentials(credentials).build();
                authorization.validate();
                settings.setAuthorization(authorization);
            }
            rules.getWebhookSettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isDefault()) {
                this.defaultHooks.add(settings.getFormattedName());
            }
            this.allHooks.add(settings.getFormattedName());
        });
    }

    /**
     * Read grpc hook config into {@link GRPCAlarmSetting}
     */
    @SuppressWarnings("unchecked")
    private void readGrpcConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.gRPC.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map config = (Map) v;
            GRPCAlarmSetting setting = new GRPCAlarmSetting(
                k.toString(), AlarmHooksType.gRPC, (Boolean) config.getOrDefault("is-default", false));

            Object targetHost = config.get("target-host");
            if (targetHost != null) {
                setting.setTargetHost((String) targetHost);
            }

            Object targetPort = config.get("target-port");
            if (targetPort != null) {
                setting.setTargetPort((Integer) targetPort);
            }

            rules.getGrpcAlarmSettingMap().put(setting.getFormattedName(), setting);

            if (setting.isDefault()) {
                this.defaultHooks.add(setting.getFormattedName());
            }
            this.allHooks.add(setting.getFormattedName());
        });
    }

    /**
     * Read slack hook config into {@link SlackSettings}
     */
    @SuppressWarnings("unchecked")
    private void readSlackConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.slack.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            SlackSettings settings = new SlackSettings(
                k.toString(), AlarmHooksType.slack, (Boolean) config.getOrDefault("is-default", false));

            Object textTemplate = config.getOrDefault("text-template", "");
            settings.setTextTemplate((String) textTemplate);

            List<String> webhooks = (List<String>) config.get("webhooks");
            if (webhooks != null) {
                settings.getWebhooks().addAll(webhooks);
            }
            rules.getSlackSettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isDefault()) {
                this.defaultHooks.add(settings.getFormattedName());
            }
            this.allHooks.add(settings.getFormattedName());
        });
    }

    /**
     * Read wechat hook config into {@link WechatSettings}
     */
    @SuppressWarnings("unchecked")
    private void readWechatConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.wechat.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            WechatSettings settings = new WechatSettings(
                k.toString(), AlarmHooksType.wechat, (Boolean) config.getOrDefault("is-default", false));

            Object textTemplate = config.getOrDefault("text-template", "");
            settings.setTextTemplate((String) textTemplate);

            List<String> webhooks = (List<String>) config.get("webhooks");
            if (webhooks != null) {
                settings.getWebhooks().addAll(webhooks);
            }
            rules.getWechatSettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isDefault()) {
                this.defaultHooks.add(settings.getFormattedName());
            }
            this.allHooks.add(settings.getFormattedName());
        });
    }

    /**
     * Read dingtalk hook config into {@link DingtalkSettings}
     */
    @SuppressWarnings("unchecked")
    private void readDingtalkConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.dingtalk.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            DingtalkSettings settings = new DingtalkSettings(
                k.toString(), AlarmHooksType.dingtalk, (Boolean) config.getOrDefault("is-default", false));

            Object textTemplate = config.getOrDefault("text-template", "");
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
            if (settings.isDefault()) {
                this.defaultHooks.add(settings.getFormattedName());
            }
            this.allHooks.add(settings.getFormattedName());
        });
    }

    /**
     * Read feishu hook config into {@link FeishuSettings}
     */
    @SuppressWarnings("unchecked")
    private void readFeishuConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.feishu.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            FeishuSettings settings = new FeishuSettings(
                k.toString(), AlarmHooksType.feishu, (Boolean) config.getOrDefault("is-default", false));

            Object textTemplate = config.getOrDefault("text-template", "");
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
            if (settings.isDefault()) {
                this.defaultHooks.add(settings.getFormattedName());
            }
            this.allHooks.add(settings.getFormattedName());
        });
    }

    /**
     * Read WeLink hook config into {@link WeLinkSettings}
     */
    @SuppressWarnings("unchecked")
    private void readWeLinkConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.welink.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            String textTemplate = (String) config.get("text-template");
            List<Map<String, String>> webhooks = (List<Map<String, String>>) config.get("webhooks");
            if (StringUtil.isBlank(textTemplate) || CollectionUtils.isEmpty(webhooks)) {
                return;
            }
            List<WeLinkSettings.WebHookUrl> webHookUrls = webhooks.stream().map(
                WeLinkSettings.WebHookUrl::generateFromMap
            ).collect(Collectors.toList());

            WeLinkSettings settings = new WeLinkSettings(
                k.toString(), AlarmHooksType.welink, (Boolean) config.getOrDefault("is-default", false));
            settings.setTextTemplate(textTemplate);
            settings.setWebhooks(webHookUrls);

            rules.getWeLinkSettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isDefault()) {
                this.defaultHooks.add(settings.getFormattedName());
            }
            this.allHooks.add(settings.getFormattedName());
        });
    }

    /**
     * Read PagerDuty hook config into {@link PagerDutySettings}
     */
    @SuppressWarnings("unchecked")
    private void readPagerDutyConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.pagerduty.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            PagerDutySettings settings = new PagerDutySettings(
                k.toString(), AlarmHooksType.pagerduty, (Boolean) config.getOrDefault("is-default", false));
            Object textTemplate = config.getOrDefault("text-template", "");
            settings.setTextTemplate((String) textTemplate);

            List<String> integrationKeys = (List<String>) config.get("integration-keys");
            if (integrationKeys != null) {
                settings.getIntegrationKeys().addAll(integrationKeys);
            }

            rules.getPagerDutySettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isDefault()) {
                this.defaultHooks.add(settings.getFormattedName());
            }
            this.allHooks.add(settings.getFormattedName());
        });
    }

    /**
     * Read Discord hook config into {@link DiscordSettings}
     */
    @SuppressWarnings("unchecked")
    private void readDiscordConfig(Map hooks, Rules rules) {
        Map configs = (Map) hooks.get(AlarmHooksType.discord.name());
        if (configs == null) {
            return;
        }
        configs.forEach((k, v) -> {
            Map<String, Object> config = (Map<String, Object>) v;
            String textTemplate = (String) config.get("text-template");
            List<Map<String, String>> webhooks = (List<Map<String, String>>) config.get("webhooks");
            if (StringUtil.isBlank(textTemplate) || CollectionUtils.isEmpty(webhooks)) {
                return;
            }
            List<DiscordSettings.WebHookUrl> webHookUrls = webhooks.stream().map(
                DiscordSettings.WebHookUrl::generateFromMap
            ).collect(Collectors.toList());

            DiscordSettings settings = new DiscordSettings(
                k.toString(), AlarmHooksType.discord, (Boolean) config.getOrDefault("is-default", false));
            settings.setTextTemplate(textTemplate);
            settings.setWebhooks(webHookUrls);

            rules.getDiscordSettingsMap().put(settings.getFormattedName(), settings);
            if (settings.isDefault()) {
                this.defaultHooks.add(settings.getFormattedName());
            }
            this.allHooks.add(settings.getFormattedName());
        });
    }

    private void checkSpecificHooks(String ruleName, Set<String> hooks) {
        if (!this.allHooks.containsAll(hooks)) {
            throw new IllegalArgumentException("rule: [" + ruleName + "] contains invalid hooks." +
                                                   " Please check the hook is exist and name format is {hookType}.{hookName}");
        }
    }
}
