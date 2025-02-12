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

package org.apache.skywalking.oap.query.debug;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmModule;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmEntity;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmModuleProvider;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.RunningRule;
import org.apache.skywalking.oap.server.core.analysis.metrics.DoubleValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
@ExceptionHandler(StatusQueryExceptionHandler.class)
public class AlarmStatusQueryHandler {
    private final Gson gson = new Gson();
    private final ModuleManager moduleManager;
    private AlarmRulesWatcher alarmRulesWatcher;

    public AlarmStatusQueryHandler(final ModuleManager manager) {
        this.moduleManager = manager;
    }

    private AlarmRulesWatcher getAlarmRulesWatcher() {
        if (alarmRulesWatcher == null) {

            AlarmModuleProvider provider = (AlarmModuleProvider) moduleManager.find(AlarmModule.NAME)
                                               .provider();
            alarmRulesWatcher = provider.getAlarmRulesWatcher();
        }
        return alarmRulesWatcher;
    }

    @Get("/status/alarm/rules")
    public HttpResponse getAlarmRules() {
        Map<String, RunningRule> runningRules = getAlarmRulesWatcher().getRunningContext().values().stream().map(List::stream)
            .flatMap(r -> r).collect(Collectors.toMap(RunningRule::getRuleName, r -> r));
        JsonObject runningRuleNames = new JsonObject();
        JsonArray nameList = new JsonArray();
        runningRuleNames.add("ruleNames", nameList);
        runningRules.keySet().forEach(nameList::add);
        return HttpResponse.of(MediaType.JSON_UTF_8, gson.toJson(runningRuleNames));
    }

    @Get("/status/alarm/{ruleName}")
    public HttpResponse getAlarmRuleByName(@Param("ruleName") String ruleName) {
        Map<String, RunningRule> runningRules = getAlarmRulesWatcher().getRunningContext().values().stream().flatMap(List::stream)
                                                                      .collect(Collectors.toMap(RunningRule::getRuleName, r -> r));
        RunningRule rule = runningRules.get(ruleName);
        JsonObject runningRuleInfo = new JsonObject();
        runningRuleInfo.addProperty("ruleName", rule.getRuleName());
        runningRuleInfo.addProperty("expression", rule.getExpression());
        runningRuleInfo.addProperty("period", rule.getPeriod());
        runningRuleInfo.addProperty("silentPeriod", rule.getSilencePeriod());
        runningRuleInfo.addProperty("additonalPeriod", rule.getAdditionalPeriod());

        JsonArray includeNameList = new JsonArray();
        runningRuleInfo.add("includeNames", includeNameList);
        rule.getIncludeNames().forEach(includeNameList::add);

        JsonArray excludeNameList = new JsonArray();
        runningRuleInfo.add("excludeNames", excludeNameList);
        rule.getExcludeNames().forEach(excludeNameList::add);

        runningRuleInfo.addProperty("includeNamesRegex", rule.getExcludeNamesRegex() == null ? "" : rule.getIncludeNamesRegex().toString());
        runningRuleInfo.addProperty("excludeNamesRegex", rule.getExcludeNamesRegex() == null ? "" : rule.getExcludeNamesRegex().toString());

        JsonArray affectedEntities = new JsonArray();
        runningRuleInfo.add("affectedEntities", affectedEntities);
        JsonArray msgFormatter = new JsonArray();
        rule.getWindows().keySet().forEach(e -> {
            JsonObject entity = new JsonObject();
            entity.addProperty("scope", e.getScope());
            entity.addProperty("name", e.getName());
            affectedEntities.add(entity);
            JsonObject msg = new JsonObject();
            msg.addProperty(e.getName(), rule.getFormatter().format(e));
            msgFormatter.add(msg);
        });

        JsonArray tagList = new JsonArray();
        runningRuleInfo.add("tags", tagList);
        rule.getTags().forEach(tag -> {
            JsonObject tagInfo = new JsonObject();
            tagInfo.addProperty("key", tag.getKey());
            tagInfo.addProperty("value", tag.getValue());
            tagList.add(tagInfo);
        });

        JsonArray hookList = new JsonArray();
        runningRuleInfo.add("hooks", hookList);
        rule.getHooks().forEach(hookList::add);

        JsonArray includeMetricList = new JsonArray();
        runningRuleInfo.add("includeMetrics", includeMetricList);
        rule.getIncludeMetrics().forEach(includeMetricList::add);

        runningRuleInfo.add("formattedMessages", msgFormatter);

        return HttpResponse.of(MediaType.JSON_UTF_8, runningRuleInfo.toString());
    }

    @Get("/status/alarm/{ruleName}/{entityName}")
    public HttpResponse getAlarmRuleContext(@Param("ruleName") String ruleName, @Param("entityName") String entityName) {
        Map<String, RunningRule> runningRules = getAlarmRulesWatcher().getRunningContext().values().stream().flatMap(List::stream)
                                                                      .collect(Collectors.toMap(RunningRule::getRuleName, r -> r));
        RunningRule rule = runningRules.get(ruleName);
        Map<AlarmEntity, RunningRule.Window> windows = rule.getWindows();
        RunningRule.Window window = windows.keySet().stream().filter(e -> e.getName().equals(entityName)).map(windows::get)
            .findFirst().orElse(null);
        JsonObject runningContext = new JsonObject();
        if (window == null) {
            return HttpResponse.of(MediaType.JSON_UTF_8, runningContext.toString());
        }

        runningContext.addProperty("expression", rule.getExpression());
        runningContext.addProperty("endTime", window.getEndTime().toString());
        runningContext.addProperty("additionalPeriod", window.getAdditionalPeriod());
        runningContext.addProperty("size", window.getSize());
        runningContext.addProperty("silenceCountdown", window.getSilenceCountdown());

        JsonArray metricValues = new JsonArray();
        runningContext.add("windowValues", metricValues);

        window.scanWindowValues(values -> {
            for (int i = 0; i < values.size(); i++) {
                JsonObject index = new JsonObject();
                JsonArray metrics = new JsonArray();
                metricValues.add(index);
                index.addProperty("index", i);
                index.add("metrics", metrics);
                Map<String, Metrics> m = values.get(i);
                if (null != m) {
                    m.forEach((name, metric) -> {
                        JsonObject metricValue = new JsonObject();
                        metricValue.addProperty("timeBucket", metric.getTimeBucket());
                        metricValue.addProperty("name", name);
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
                        metricValue.addProperty("value", value);
                        metrics.add(metricValue);
                    });
                }
            }
        });

        runningContext.add("mqeMetricsSnapshot", window.getMqeMetricsSnapshot());
        return HttpResponse.of(MediaType.JSON_UTF_8, gson.toJson(runningContext));
    }
}
