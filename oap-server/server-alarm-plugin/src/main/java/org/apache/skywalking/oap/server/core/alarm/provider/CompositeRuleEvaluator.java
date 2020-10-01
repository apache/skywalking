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
import lombok.AllArgsConstructor;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.expression.Expression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluator composite rule using expression eval
 *
 * @since 8.2.0
 */
@AllArgsConstructor
public class CompositeRuleEvaluator {

    private Expression expression;

    /**
     * Evaluator composite rule
     *
     * @param compositeAlarmRules compositeRules
     * @param alarmMessages       triggered alarm messages
     * @return
     */
    public List<AlarmMessage> evaluator(List<CompositeAlarmRule> compositeAlarmRules, List<AlarmMessage> alarmMessages) {
        final List<AlarmMessage> compositeRuleMessages = new ArrayList<>();
        ImmutableListMultimap<String, AlarmMessage> messageMap = Multimaps.index(alarmMessages, alarmMessage -> Joiner.on("_").useForNull("").join(alarmMessage.getId0(), alarmMessage.getId1()));
        for (CompositeAlarmRule compositeAlarmRule : compositeAlarmRules) {
            String expr = compositeAlarmRule.getExpression();
            Set<String> dependencyRules = expression.analysisInputs(expr);
            Map<String, Object> dataContext = new HashMap<>();
            messageMap.asMap().forEach((key, alarmMessageList) -> {
                Set<String> allRuleNames = new HashSet<>(dependencyRules);
                alarmMessageList.forEach(alarmMessage -> {
                    if (allRuleNames.remove(alarmMessage.getRuleName())) {
                        dataContext.put(alarmMessage.getRuleName(), true);
                    }
                });
                allRuleNames.forEach(ruleName -> {
                    dataContext.put(ruleName, false);
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
                    message.setAlarmMessage(compositeAlarmRule.getMessage());
                    compositeRuleMessages.add(message);
                }
            });
        }
        return compositeRuleMessages;
    }
}
