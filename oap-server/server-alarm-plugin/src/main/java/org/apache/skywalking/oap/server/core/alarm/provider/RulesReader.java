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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.alarm.provider.dingtalk.DingtalkSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.feishu.FeishuSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.grpc.GRPCAlarmSetting;
import org.apache.skywalking.oap.server.core.alarm.provider.slack.SlackSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.wechat.WechatSettings;
import org.apache.skywalking.oap.server.core.alarm.provider.welink.WeLinkSettings;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Rule Reader parses the given `alarm-settings.yml` config file, to the target {@link Rules}.
 */
public class RulesReader {
    private Map yamlData;

    public RulesReader(InputStream inputStream) {
        Yaml yaml = new Yaml(new SafeConstructor());
        yamlData = (Map) yaml.load(inputStream);
    }

    public RulesReader(Reader io) {
        Yaml yaml = new Yaml(new SafeConstructor());
        yamlData = (Map) yaml.load(io);
    }

    /**
     * Read rule config file to {@link Rules}
     */
    public Rules readRules() {
        Rules rules = new Rules();

        if (Objects.nonNull(yamlData)) {
            readRulesConfig(rules);
            readWebHookConfig(rules);
            readGrpcConfig(rules);
            readSlackConfig(rules);
            readWechatConfig(rules);
            readCompositeRuleConfig(rules);
            readDingtalkConfig(rules);
            readFeishuConfig(rules);
            readWeLinkConfig(rules);
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
                rules.getRules().add(alarmRule);
            }
        });
    }

    /**
     * Read web hook config
     */
    private void readWebHookConfig(Rules rules) {
        List webhooks = (List) yamlData.get("webhooks");
        if (webhooks != null) {
            rules.setWebhooks(new ArrayList<>());
            webhooks.forEach(url -> {
                rules.getWebhooks().add((String) url);
            });
        }
    }

    /**
     * Read grpc hook config into {@link GRPCAlarmSetting}
     */
    private void readGrpcConfig(Rules rules) {
        Map grpchooks = (Map) yamlData.get("gRPCHook");
        if (grpchooks != null) {
            GRPCAlarmSetting grpcAlarmSetting = new GRPCAlarmSetting();
            Object targetHost = grpchooks.get("target_host");
            if (targetHost != null) {
                grpcAlarmSetting.setTargetHost((String) targetHost);
            }

            Object targetPort = grpchooks.get("target_port");
            if (targetPort != null) {
                grpcAlarmSetting.setTargetPort((Integer) targetPort);
            }

            rules.setGrpchookSetting(grpcAlarmSetting);
        }
    }

    /**
     * Read slack hook config into {@link SlackSettings}
     */
    private void readSlackConfig(Rules rules) {
        Map slacks = (Map) yamlData.get("slackHooks");
        if (slacks != null) {
            SlackSettings slackSettings = new SlackSettings();
            Object textTemplate = slacks.getOrDefault("textTemplate", "");
            slackSettings.setTextTemplate((String) textTemplate);

            List<String> slackWebhooks = (List<String>) slacks.get("webhooks");
            if (slackWebhooks != null) {
                slackSettings.getWebhooks().addAll(slackWebhooks);
            }
            rules.setSlacks(slackSettings);
        }
    }

    /**
     * Read wechat hook config into {@link WechatSettings}
     */
    private void readWechatConfig(Rules rules) {
        Map wechatConfig = (Map) yamlData.get("wechatHooks");
        if (wechatConfig != null) {
            WechatSettings wechatSettings = new WechatSettings();
            Object textTemplate = wechatConfig.getOrDefault("textTemplate", "");
            wechatSettings.setTextTemplate((String) textTemplate);
            List<String> wechatWebhooks = (List<String>) wechatConfig.get("webhooks");
            if (wechatWebhooks != null) {
                wechatSettings.getWebhooks().addAll(wechatWebhooks);
            }
            rules.setWecchats(wechatSettings);
        }
    }

    /**
     * Read composite rule config into {@link CompositeAlarmRule}
     */
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
                rules.getCompositeRules().add(compositeAlarmRule);
            }
        });
    }

    /**
     * Read dingtalk hook config into {@link DingtalkSettings}
     */
    private void readDingtalkConfig(Rules rules) {
        Map dingtalkConfig = (Map) yamlData.get("dingtalkHooks");
        if (dingtalkConfig != null) {
            DingtalkSettings dingtalkSettings = new DingtalkSettings();
            Object textTemplate = dingtalkConfig.getOrDefault("textTemplate", "");
            dingtalkSettings.setTextTemplate((String) textTemplate);
            List<Map<String, Object>> wechatWebhooks = (List<Map<String, Object>>) dingtalkConfig.get("webhooks");
            if (wechatWebhooks != null) {
                wechatWebhooks.forEach(wechatWebhook -> {
                    Object secret = wechatWebhook.getOrDefault("secret", "");
                    Object url = wechatWebhook.getOrDefault("url", "");
                    dingtalkSettings.getWebhooks().add(new DingtalkSettings.WebHookUrl((String) secret, (String) url));
                });
            }
            rules.setDingtalks(dingtalkSettings);
        }
    }

    /**
     * Read feishu hook config into {@link FeishuSettings}
     */
    private void readFeishuConfig(Rules rules) {
        Map feishuConfig = (Map) yamlData.get("feishuHooks");
        if (feishuConfig != null) {
            FeishuSettings feishuSettings = new FeishuSettings();
            Object textTemplate = feishuConfig.getOrDefault("textTemplate", "");
            feishuSettings.setTextTemplate((String) textTemplate);
            List<Map<String, Object>> wechatWebhooks = (List<Map<String, Object>>) feishuConfig.get("webhooks");
            if (wechatWebhooks != null) {
                wechatWebhooks.forEach(wechatWebhook -> {
                    Object secret = wechatWebhook.getOrDefault("secret", "");
                    Object url = wechatWebhook.getOrDefault("url", "");
                    feishuSettings.getWebhooks().add(new FeishuSettings.WebHookUrl((String) secret, (String) url));
                });
            }
            rules.setFeishus(feishuSettings);
        }
    }

    /**
     * Read WeLink hook config into {@link WeLinkSettings}
     */
    @SuppressWarnings("unchecked")
    private void readWeLinkConfig(Rules rules) {
        Map<String, Object> welinkConfig = (Map<String, Object>) yamlData.getOrDefault(
            "welinkHooks",
            Collections.EMPTY_MAP
        );
        String textTemplate = (String) welinkConfig.get("textTemplate");
        List<Map<String, String>> welinkWebHooks = (List<Map<String, String>>) welinkConfig.get("webhooks");
        if (StringUtil.isBlank(textTemplate) || CollectionUtils.isEmpty(welinkWebHooks)) {
            return;
        }
        List<WeLinkSettings.WebHookUrl> webHookUrls = welinkWebHooks.stream().map(
            WeLinkSettings.WebHookUrl::generateFromMap
        ).collect(Collectors.toList());

        WeLinkSettings welinkSettings = new WeLinkSettings();
        welinkSettings.setTextTemplate(textTemplate);
        welinkSettings.setWebhooks(webHookUrls);
        rules.setWelinks(welinkSettings);
    }
}
