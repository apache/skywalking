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

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.alarm.AlarmModule;
import org.apache.skywalking.oap.server.core.alarm.AlarmRulesWatcherService;
import org.apache.skywalking.oap.server.core.alarm.AlarmStatusWatcherService;
import org.apache.skywalking.oap.server.core.alarm.provider.status.AlarmRuleDetail;
import org.apache.skywalking.oap.server.core.alarm.provider.status.AlarmRuleList;
import org.apache.skywalking.oap.server.core.alarm.provider.status.AlarmRunningContext;
import org.apache.skywalking.oap.server.core.analysis.metrics.DoubleValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class AlarmStatusWatcher implements AlarmStatusWatcherService {
    private final static Gson GSON = new Gson();
    private AlarmRulesWatcherService rulesWatcherService;
    private final ModuleManager moduleManager;
    private AlarmRulesWatcher alarmRulesWatcher;

    public AlarmStatusWatcher(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private AlarmRulesWatcher getAlarmRulesWatcher() {
        if (alarmRulesWatcher == null) {
            alarmRulesWatcher = (AlarmRulesWatcher) moduleManager.find(AlarmModule.NAME)
                                                                 .provider().getService(AlarmRulesWatcherService.class);
        }
        return alarmRulesWatcher;
    }

    @Override
    public String getAlarmRules() {
        Map<String, RunningRule> runningRules = getAlarmRulesWatcher().getRunningContext()
                                                                      .values()
                                                                      .stream()
                                                                      .map(List::stream)
                                                                      .flatMap(r -> r)
                                                                      .collect(
                                                                          Collectors.toMap(
                                                                              RunningRule::getRuleName, r -> r));
        AlarmRuleList alarmRuleList = new AlarmRuleList();
        runningRules.keySet().forEach(ruleName -> {
            AlarmRuleList.RuleInfo alarmRule = new AlarmRuleList.RuleInfo();
            alarmRule.setId(ruleName);
            alarmRuleList.getRuleList().add(alarmRule);
        });
        return GSON.toJson(alarmRuleList);
    }

    @Override
    public String getAlarmRuleById(final String ruleId) {
        AlarmRuleDetail ruleDetail = new AlarmRuleDetail();
        Map<String, RunningRule> runningRules = getAlarmRulesWatcher().getRunningContext()
                                                                      .values()
                                                                      .stream()
                                                                      .flatMap(List::stream)
                                                                      .collect(
                                                                          Collectors.toMap(
                                                                              RunningRule::getRuleName,
                                                                              r -> r
                                                                          ));
        RunningRule rule = runningRules.get(ruleId);
        if (rule == null) {
            return "";
        }
        ruleDetail.setRuleId(rule.getRuleName());
        ruleDetail.setExpression(rule.getExpression());
        ruleDetail.setPeriod(rule.getPeriod());
        ruleDetail.setSilencePeriod(rule.getSilencePeriod());
        ruleDetail.setRecoveryObservationPeriod(rule.getRecoveryObservationPeriod());
        ruleDetail.setAdditionalPeriod(rule.getAdditionalPeriod());
        ruleDetail.setIncludeEntityNames(rule.getIncludeNames());
        ruleDetail.setExcludeEntityNames(rule.getExcludeNames());
        ruleDetail.setIncludeEntityNamesRegex(
            rule.getIncludeNamesRegex() == null ? "" : rule.getIncludeNamesRegex().toString());
        ruleDetail.setExcludeEntityNamesRegex(
            rule.getExcludeNamesRegex() == null ? "" : rule.getExcludeNamesRegex().toString());
        ruleDetail.setTags(rule.getTags());
        ruleDetail.setHooks(rule.getHooks());
        ruleDetail.setIncludeMetrics(rule.getIncludeMetrics());
        Map<AlarmEntity, RunningRule.Window> windows = rule.getWindows();
        windows.keySet().forEach(e -> {
            AlarmRuleDetail.RunningEntity entity = new AlarmRuleDetail.RunningEntity();
            entity.setScope(e.getScope());
            entity.setName(e.getName());
            entity.setFormattedMessage(rule.getFormatter().format(e));
            ruleDetail.getRunningEntities().add(entity);
        });

        return GSON.toJson(ruleDetail);
    }

    @Override
    public String getAlarmRuleContext(final String ruleName, final String entityName) {
        Map<String, RunningRule> runningRules = getAlarmRulesWatcher().getRunningContext().values().stream().flatMap(List::stream)
                                                                      .collect(Collectors.toMap(RunningRule::getRuleName, r -> r));
        RunningRule rule = runningRules.get(ruleName);
        if (rule == null) {
            return "";
        }
        AlarmRunningContext runningContext = new AlarmRunningContext();
        runningContext.setRuleId(rule.getRuleName());
        runningContext.setExpression(rule.getExpression());
        Map<AlarmEntity, RunningRule.Window> windows = rule.getWindows();
        RunningRule.Window window = windows.keySet().stream().filter(e -> e.getName().equals(entityName)).map(windows::get)
                                           .findFirst().orElse(null);
        if (window == null) {
            return GSON.toJson(runningContext);
        }
        runningContext.setEntityName(entityName);
        runningContext.setEndTime(window.getEndTime().toString());
        runningContext.setAdditionalPeriod(window.getAdditionalPeriod());
        runningContext.setSize(window.getSize());
        runningContext.setSilenceCountdown(window.getStateMachine().getSilenceCountdown());
        runningContext.setRecoveryObservationCountdown(window.getStateMachine().getRecoveryObservationCountdown());
        window.scanWindowValues(values -> {
            for (int i = 0; i < values.size(); i++) {
                AlarmRunningContext.WindowValue windowValue = new AlarmRunningContext.WindowValue();
                runningContext.getWindowValues().add(windowValue);
                windowValue.setIndex(i);
                Map<String, Metrics> m = values.get(i);
                if (null != m) {
                    m.forEach((name, metric) -> {
                        AlarmRunningContext.Metric metricValue = new AlarmRunningContext.Metric();
                        metricValue.setTimeBucket(metric.getTimeBucket());
                        metricValue.setName(name);
                        String value = "";
                        if (metric instanceof LongValueHolder) {
                            value = Long.toString(((LongValueHolder) metric).getValue());
                        } else if (metric instanceof IntValueHolder) {
                            value = Integer.toString(((IntValueHolder) metric).getValue());
                        } else if (metric instanceof DoubleValueHolder) {
                            value = Double.toString(((DoubleValueHolder) metric).getValue());
                        } else if (metric instanceof LabeledValueHolder) {
                            value = ((LabeledValueHolder) metric).getValue().toString();
                        }
                        metricValue.setValue(value);
                        windowValue.getMetrics().add(metricValue);
                    });
                }
            }
        });
        runningContext.setMqeMetricsSnapshot(window.getMqeMetricsSnapshot());
        return GSON.toJson(runningContext);
    }
}
