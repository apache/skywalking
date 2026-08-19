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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.ai.evaluation.task.EvaluationTask;

@Slf4j
public class EvaluationResultParser {
    public List<EvaluationResult> parse(final EvaluationPlan plan, final String content) {
        final JsonObject root = JsonParser.parseString(stripMarkdownCodeFence(content)).getAsJsonObject();
        final List<EvaluationResult> results = new ArrayList<>();
        for (EvaluationTask task : plan.getTasks()) {
            try {
                final JsonElement metricElement = root.get(task.getName());
                if (metricElement == null || !metricElement.isJsonObject()) {
                    log.warn("Skip missing or malformed GenAI evaluation result, task: {}", task.getName());
                    continue;
                }

                final JsonObject metric = metricElement.getAsJsonObject();
                final String value = getAsString(metric, "value");
                final String reason = getAsString(metric, "reason");
                validate(task, value);
                results.add(EvaluationResult.builder()
                                            .name(task.getName())
                                            .valueType(task.getValueType())
                                            .value(value)
                                            .reason(reason)
                                            .build());
            } catch (RuntimeException ignored) {
                log.warn("Skip invalid GenAI evaluation result, task: {}", task.getName());
            }
        }
        return results;
    }

    private static String stripMarkdownCodeFence(final String content) {
        if (content == null) {
            return "";
        }

        final String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        final int contentStart = trimmed.indexOf('\n');
        final int contentEnd = trimmed.lastIndexOf("```");
        if (contentStart < 0 || contentEnd <= contentStart) {
            return trimmed;
        }
        return trimmed.substring(contentStart + 1, contentEnd).trim();
    }

    private static void validate(final EvaluationTask task, final String value) {
        switch (task.getValueType()) {
            case SCORE:
                validateScore(task, value);
                break;
            case BOOLEAN:
                validateBoolean(task, value);
                break;
            case STRING:
                validateString(task, value);
                break;
            case JSON:
                validateJson(task, value);
                break;
            default:
                break;
        }
    }

    private static void validateScore(final EvaluationTask task, final String value) {
        try {
            final double score = Double.parseDouble(value);
            if (!Double.isFinite(score) || score < 0 || score > 1) {
                throw new IllegalArgumentException("Invalid score value of " + task.getName() + ": " + value);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid score value of " + task.getName() + ": " + value, e);
        }
    }

    private static void validateBoolean(final EvaluationTask task, final String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Invalid boolean value of " + task.getName() + ": " + value);
        }
    }

    private static void validateString(final EvaluationTask task, final String value) {
        if (task.getAllowedValues() == null || task.getAllowedValues().isEmpty()) {
            return;
        }
        for (String allowedValue : task.getAllowedValues()) {
            if (allowedValue.equalsIgnoreCase(value)) {
                return;
            }
        }
        throw new IllegalArgumentException("Invalid string value of " + task.getName() + ": " + value);
    }

    private static void validateJson(final EvaluationTask task, final String value) {
        final JsonElement element;
        try {
            element = JsonParser.parseString(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid JSON value of " + task.getName() + ": " + value, e);
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(
                "JSON value of " + task.getName() + " must be an object: " + value
            );
        }
    }

    private static String getAsString(final JsonObject object, final String memberName) {
        final JsonElement element = object.get(memberName);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }
}
