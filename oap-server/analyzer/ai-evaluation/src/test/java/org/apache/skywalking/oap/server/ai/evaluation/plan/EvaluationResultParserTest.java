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
 */

package org.apache.skywalking.oap.server.ai.evaluation.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.apache.skywalking.oap.server.ai.evaluation.task.EvaluationTask;
import org.apache.skywalking.oap.server.ai.evaluation.value.ValueType;
import org.junit.jupiter.api.Test;

class EvaluationResultParserTest {
    private final EvaluationResultParser parser = new EvaluationResultParser();

    @Test
    void shouldStripMarkdownCodeFence() {
        final EvaluationPlan plan = plan(task("quality", ValueType.SCORE));
        final String content = "```json\n"
            + "{\"quality\":{\"value\":\"0.86\",\"reason\":\"good\"}}\n"
            + "```";

        final List<EvaluationResult> results = parser.parse(plan, content);

        assertEquals(1, results.size());
        assertEquals("quality", results.get(0).getName());
        assertEquals("0.86", results.get(0).getValue());
        assertEquals("good", results.get(0).getReason());
    }

    @Test
    void shouldKeepValidResultsWhenOtherTasksAreInvalidOrMissing() {
        final EvaluationPlan plan = plan(
            task("grounded", ValueType.BOOLEAN),
            task("quality", ValueType.SCORE),
            task("summary", ValueType.STRING)
        );
        final String content = "{"
            + "\"quality\":{\"value\":\"0.86\",\"reason\":\"good\"},"
            + "\"grounded\":{\"value\":\"not-a-boolean\",\"reason\":\"invalid\"}"
            + "}";

        final List<EvaluationResult> results = parser.parse(plan, content);

        assertEquals(1, results.size());
        assertEquals("quality", results.get(0).getName());
    }

    @Test
    void shouldParseJsonObjectValue() {
        final EvaluationPlan plan = plan(task("metadata", ValueType.JSON));
        final String content = "{"
            + "\"metadata\":{\"value\":{\"a\":1,\"nested\":{\"b\":true}},\"reason\":\"details\"}"
            + "}";

        final List<EvaluationResult> results = parser.parse(plan, content);

        assertEquals(1, results.size());
        assertEquals("{\"a\":1,\"nested\":{\"b\":true}}", results.get(0).getValue());
        assertEquals("details", results.get(0).getReason());
    }

    @Test
    void shouldRejectNonObjectJsonValue() {
        final EvaluationPlan plan = plan(task("metadata", ValueType.JSON));
        final String content = "{\"metadata\":{\"value\":\"plain-text\",\"reason\":\"invalid\"}}";

        final List<EvaluationResult> results = parser.parse(plan, content);

        assertEquals(0, results.size());
    }

    @Test
    void shouldRejectMalformedRootResponse() {
        final EvaluationPlan plan = plan(task("quality", ValueType.SCORE));

        assertThrows(RuntimeException.class, () -> parser.parse(plan, "not-json"));
    }

    private static EvaluationPlan plan(final EvaluationTask... tasks) {
        return EvaluationPlan.builder().tasks(List.of(tasks)).build();
    }

    private static EvaluationTask task(final String name, final ValueType valueType) {
        final EvaluationTask task = new EvaluationTask();
        task.setName(name);
        task.setValueType(valueType);
        return task;
    }
}
