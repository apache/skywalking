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

package org.apache.skywalking.oap.server.ai.evaluation.level;

import java.util.List;
import org.apache.skywalking.oap.server.ai.evaluation.value.ValueType;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public class EvaluationLevelResolver {
    public static final String FAIL = "fail";
    public static final String EXCELLENT = "excellent";
    public static final String UNDEFINED = "undefined";

    private final EvaluationLevelConfig config;

    public EvaluationLevelResolver(final EvaluationLevelConfig config) {
        this.config = config == null ? new EvaluationLevelConfig() : config;
    }

    public String resolve(final ValueType valueType, final String value) {
        if (valueType == null || value == null) {
            return undefinedLevel();
        }
        switch (valueType) {
            case SCORE:
                return resolveScore(value);
            case BOOLEAN:
                return resolveBoolean(value);
            default:
                return undefinedLevel();
        }
    }

    private String resolveScore(final String value) {
        final double score;
        try {
            score = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return undefinedLevel();
        }
        final List<ScoreLevelRule> rules = config.getScore();
        if (rules == null || rules.isEmpty()) {
            return undefinedLevel();
        }
        return rules.stream()
                    .filter(rule -> rule.matches(score))
                    .map(ScoreLevelRule::getLevel)
                    .filter(StringUtil::isNotEmpty)
                    .findFirst()
                    .orElseGet(this::undefinedLevel);
    }

    private String resolveBoolean(final String value) {
        if ("true".equalsIgnoreCase(value)) {
            return defaultIfEmpty(config.getBooleanTrue(), undefinedLevel());
        }
        if ("false".equalsIgnoreCase(value)) {
            return defaultIfEmpty(config.getBooleanFalse(), undefinedLevel());
        }
        return undefinedLevel();
    }

    private String undefinedLevel() {
        return defaultIfEmpty(config.getUndefined(), UNDEFINED);
    }

    private static String defaultIfEmpty(final String value, final String defaultValue) {
        return StringUtil.isNotEmpty(value) ? value : defaultValue;
    }
}
