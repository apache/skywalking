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

import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelRequest;
import org.apache.skywalking.oap.server.ai.evaluation.task.EvaluationTask;

public class EvaluationPromptBuilder {
    private static final String INPUT_MESSAGES = "input_messages";
    private static final String OUTPUT_MESSAGES = "output_messages";

    private final String systemPrompt;

    public EvaluationPromptBuilder(final String systemPrompt) {
        this.systemPrompt = defaultString(systemPrompt);
    }

    public JudgeModelRequest build(final EvaluationPlan plan) {
        return JudgeModelRequest.builder()
                                .systemPrompt(systemPrompt)
                                .userPrompt(buildUserPrompt(plan))
                                .build();
    }

    private String buildUserPrompt(final EvaluationPlan plan) {
        final StringBuilder prompt = new StringBuilder();
        prompt.append("Input messages:\n");
        prompt.append(defaultString(plan.getContext().get(INPUT_MESSAGES))).append("\n\n");

        prompt.append("Output messages:\n");
        prompt.append(defaultString(plan.getContext().get(OUTPUT_MESSAGES))).append("\n\n");

        prompt.append("Evaluation tasks:\n");
        int index = 1;
        for (EvaluationTask task : plan.getTasks()) {
            prompt.append(index++).append(". ").append(task.getName()).append("\n");
            prompt.append("valueType: ").append(task.getValueType()).append("\n");
            if (task.getAllowedValues() != null && !task.getAllowedValues().isEmpty()) {
                prompt.append("allowedValues: ").append(task.getAllowedValues()).append("\n");
            }
            prompt.append(defaultString(task.getInstruction())).append("\n\n");
        }
        return prompt.toString();
    }

    private static String defaultString(final String value) {
        return value == null ? "" : value;
    }
}
