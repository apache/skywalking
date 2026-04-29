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

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmKernelService;

/**
 * Default implementation of {@link AlarmKernelService}. Walks every {@link RunningRule} held
 * by {@link AlarmRulesWatcher} and, for any rule whose MQE expression references one of the
 * supplied metric names, invokes {@link RunningRule#resetWindows} to discard accumulated
 * per-entity window state so firing state does not carry across a metric-semantics boundary.
 *
 * <p>Match criterion uses the authoritative {@code includeMetrics} set
 * ({@link RunningRule#getIncludeMetrics}) computed by {@code AlarmMQEVerifyVisitor} at
 * rule-load time directly from the parsed MQE tree. That set is the same filter the rule's
 * {@code in()} already uses to accept / drop incoming samples, so matching against it here is
 * symmetrical: we reset exactly the windows that would observe a semantics change.
 *
 * <p>Concurrency: {@code RunningRule.windows} is a ConcurrentHashMap and
 * {@code resetWindows()} is a single atomic {@code clear()}. Sample evaluation that arrives
 * mid-reset may briefly observe fewer entities; the worst case is one missed evaluation tick
 * for one entity, which a real alarm recovers from within the next period.
 */
@Slf4j
@RequiredArgsConstructor
public class AlarmKernel implements AlarmKernelService {

    private final AlarmRulesWatcher rulesWatcher;

    @Override
    public void reset(final Set<String> affectedMetricNames) {
        if (affectedMetricNames == null || affectedMetricNames.isEmpty()) {
            return;
        }
        final Map<String, List<RunningRule>> running = rulesWatcher.getRunningContext();
        if (running == null || running.isEmpty()) {
            return;
        }
        int matched = 0;
        for (final Map.Entry<String, List<RunningRule>> entry : running.entrySet()) {
            for (final RunningRule rule : entry.getValue()) {
                final Set<String> ruleMetrics = rule.getIncludeMetrics();
                if (ruleMetrics == null || ruleMetrics.isEmpty()) {
                    continue;
                }
                String matchedMetric = null;
                for (final String metric : affectedMetricNames) {
                    if (ruleMetrics.contains(metric)) {
                        matchedMetric = metric;
                        break;
                    }
                }
                if (matchedMetric == null) {
                    continue;
                }
                rule.resetWindows();
                log.info("alarm-kernel reset: rule={} affected-metric={} (windows cleared)",
                    rule.getRuleName(), matchedMetric);
                matched++;
            }
        }
        if (matched > 0) {
            log.info("alarm-kernel reset: {} rule(s) had their windows cleared for affected metrics {}",
                matched, affectedMetricNames);
        }
    }
}
