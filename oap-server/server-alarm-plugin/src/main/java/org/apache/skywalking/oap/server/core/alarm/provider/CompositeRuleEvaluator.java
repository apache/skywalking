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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.provider.expression.Expression;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Evaluate composite rule using expression eval
 *
 * @since 8.2.0
 */
public class CompositeRuleEvaluator {

    private Expression expression;
    private Map<String, AlarmMessageFormatter> messageFormatterCache;

    public CompositeRuleEvaluator(Expression expression) {
        this.expression = expression;
        this.messageFormatterCache = new ConcurrentHashMap<>();
    }

    /**
     * Evaluate composite rule
     *
     * @param compositeAlarmRules compositeRules
     * @param alarmMessages       triggered alarm messages
     * @return
     */
    public List<AlarmMessage> evaluate(List<CompositeAlarmRule> compositeAlarmRules, List<AlarmMessage> alarmMessages) {
        final List<AlarmMessage> compositeRuleMessages = new ArrayList<>();
        ImmutableListMultimap<String, AlarmMessage> messageMap = Multimaps.index(alarmMessages, alarmMessage ->
                Joiner.on(Const.ID_CONNECTOR).useForNull(Const.EMPTY_STRING).join(alarmMessage.getId0(), alarmMessage.getId1()));
        for (CompositeAlarmRule compositeAlarmRule : compositeAlarmRules) {
            String expr = compositeAlarmRule.getExpression();
            Set<String> dependencyRules = expression.analysisInputs(expr);
            Map<String, Object> dataContext = new HashMap<>();
            messageMap.asMap().forEach((key, alarmMessageList) -> {
                dependencyRules.forEach(ruleName -> dataContext.put(ruleName, false));
                alarmMessageList.forEach(alarmMessage -> {
                    if (dependencyRules.contains(alarmMessage.getRuleName())) {
                        dataContext.put(alarmMessage.getRuleName(), true);
                    }
                });
                Object matched = expression.eval(expr, dataContext);
                if (matched instanceof Boolean && (Boolean) matched) {
                    AlarmMessage headMsg = alarmMessageList.iterator().next();
                    AlarmMessage message = new AlarmMessage();
                    message.setOnlyAsCondition(false);
                    message.setScopeId(headMsg.getScopeId());
                    message.setScope(headMsg.getScope());
                    message.setName(headMsg.getName());
                    message.setId0(headMsg.getId0());
                    message.setId1(headMsg.getId1());
                    message.setStartTime(System.currentTimeMillis());
                    message.setRuleName(compositeAlarmRule.getAlarmRuleName());
                    String alarmMessage = formatMessage(message, compositeAlarmRule.getMessage(), compositeAlarmRule.getExpression());
                    message.setAlarmMessage(alarmMessage);
                    message.setPeriod(headMsg.getPeriod());
                    message.setTags(compositeAlarmRule.getTags().entrySet().stream().map(e -> new Tag(e.getKey(), e.getValue())).collect(Collectors.toList()));
                    compositeRuleMessages.add(message);
                }
            });
        }
        return compositeRuleMessages;
    }

    /**
     * Format alarm message using {@link AlarmMessageFormatter}, only support name and id0 meta
     */
    private String formatMessage(AlarmMessage alarmMessage, String message, String metricName) {
        return messageFormatterCache.computeIfAbsent(message, AlarmMessageFormatter::new).format(new MetaInAlarm() {
            @Override
            public String getScope() {
                return alarmMessage.getScope();
            }

            @Override
            public int getScopeId() {
                return alarmMessage.getScopeId();
            }

            @Override
            public String getName() {
                return alarmMessage.getName();
            }

            @Override
            public String getMetricsName() {
                return metricName;
            }

            @Override
            public String getId0() {
                return alarmMessage.getId0();
            }

            @Override
            public String getId1() {
                return alarmMessage.getId1();
            }
        });
    }
}
