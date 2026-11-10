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

package org.apache.skywalking.oap.trace.analysis.checker;

import org.apache.skywalking.oap.server.ai.evaluation.context.AIEvaluationContext;
import org.apache.skywalking.oap.server.ai.evaluation.service.IAIEvaluationService;
import org.apache.skywalking.oap.server.ai.evaluation.service.sample.DefaultAIEvaluationSamplingPolicy;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Answers {@code shouldSample} as the service it stands for answers, the active one from its sampling policy or the
 * inactive one with false, and records every {@code sample} call's context instead of queuing it for a judge.
 */
final class CheckerEvaluationService implements IAIEvaluationService {
    private final boolean active;
    private final DefaultAIEvaluationSamplingPolicy policy;
    private final CheckerEventLog log;

    CheckerEvaluationService(final boolean active, final int sampleRate, final CheckerEventLog log) {
        this.active = active;
        this.policy = new DefaultAIEvaluationSamplingPolicy(sampleRate);
        this.log = log;
    }

    @Override
    public boolean shouldSample(final String traceId) {
        return active && StringUtil.isNotEmpty(traceId) && policy.shouldSample(traceId);
    }

    @Override
    public void sample(final AIEvaluationContext context) {
        log.sample(CheckerObjectRenderer.render(context));
    }
}
