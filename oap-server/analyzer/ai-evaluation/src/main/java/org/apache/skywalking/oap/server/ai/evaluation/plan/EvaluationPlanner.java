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

package org.apache.skywalking.oap.server.ai.evaluation.plan;

import java.util.ArrayList;
import java.util.List;

import org.apache.skywalking.oap.server.ai.evaluation.context.AIEvaluationContext;
import org.apache.skywalking.oap.server.ai.evaluation.task.EvaluationTask;
import org.apache.skywalking.oap.server.ai.evaluation.value.SpanEvaluationType;

public class EvaluationPlanner {
    private final EvaluationInputExtractor inputExtractor;

    public EvaluationPlanner(final EvaluationInputExtractor inputExtractor) {
        this.inputExtractor = inputExtractor;
    }

    public List<EvaluationPlan> plan(final AIEvaluationContext context,
                                     final List<EvaluationTask> tasks) {
        final List<EvaluationTask> effectiveTasks = new ArrayList<>();
        for (EvaluationTask task : tasks) {
            if (task == null) {
                continue;
            }
            effectiveTasks.add(task);
        }

        final List<EvaluationPlan> plans = new ArrayList<>();
        if (!effectiveTasks.isEmpty()) {
            plans.add(EvaluationPlan.builder()
                                    .spanType(SpanEvaluationType.LLM_CALL)
                                    .tasks(effectiveTasks)
                                    .context(inputExtractor.extract(context, SpanEvaluationType.LLM_CALL))
                                    .build());
        }
        return plans;
    }
}
