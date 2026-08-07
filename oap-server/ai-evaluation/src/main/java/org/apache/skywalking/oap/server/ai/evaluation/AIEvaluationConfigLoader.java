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

package org.apache.skywalking.oap.server.ai.evaluation;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import org.apache.skywalking.oap.server.ai.evaluation.level.EvaluationLevelConfig;
import org.apache.skywalking.oap.server.ai.evaluation.level.ScoreLevelRule;
import org.apache.skywalking.oap.server.ai.evaluation.task.EvaluationTask;
import org.apache.skywalking.oap.server.ai.evaluation.value.ValueType;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.library.util.YamlConfigLoaderUtils;
import org.yaml.snakeyaml.Yaml;

public class AIEvaluationConfigLoader {
    private static final String CONFIG_FILE = "ai-evaluation.yml";

    public AIEvaluationConfig load() throws ModuleStartException {
        try {
            final Reader reader = ResourceUtils.read(CONFIG_FILE);
            final Map<String, ?> loaded = new Yaml().loadAs(reader, Map.class);
            if (loaded == null || loaded.isEmpty()) {
                return new AIEvaluationConfig();
            }
            return buildConfig(loaded);
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Cannot find the AI evaluation configuration file ["
                + CONFIG_FILE + "].", e);
        }
    }

    private AIEvaluationConfig buildConfig(final Map<String, ?> loaded) {
        final AIEvaluationConfig config = new AIEvaluationConfig();

        final Object judge = loaded.get("judge");
        if (judge instanceof Map) {
            config.setJudge(buildProperties((Map<String, ?>) judge));
        }

        final Object systemPrompt = loaded.get("system-prompt");
        if (systemPrompt != null) {
            config.setSystemPrompt(String.valueOf(systemPrompt));
        }

        final Object level = loaded.get("level");
        if (level instanceof Map) {
            config.setLevel(buildLevelConfig((Map<String, ?>) level));
        }

        final Object tasks = loaded.get("tasks");
        if (tasks instanceof List) {
            for (Map<String, ?> taskConfig : (List<Map<String, ?>>) tasks) {
                config.getTasks().add(buildTask(taskConfig));
            }
        }
        return config;
    }

    private EvaluationLevelConfig buildLevelConfig(final Map<String, ?> levelConfig) {
        final EvaluationLevelConfig level = new EvaluationLevelConfig();
        setString(levelConfig, "undefined", level::setUndefined);

        final Object score = levelConfig.get("score");
        if (score instanceof List) {
            for (Map<String, ?> ruleConfig : (List<Map<String, ?>>) score) {
                level.getScore().add(buildScoreLevelRule(ruleConfig));
            }
        }

        final Object bool = levelConfig.get("boolean");
        if (bool instanceof Map) {
            final Map<String, ?> booleanConfig = (Map<String, ?>) bool;
            setString(booleanConfig, "true", level::setBooleanTrue);
            setString(booleanConfig, "false", level::setBooleanFalse);
        }
        return level;
    }

    private ScoreLevelRule buildScoreLevelRule(final Map<String, ?> ruleConfig) {
        final ScoreLevelRule rule = new ScoreLevelRule();
        final Object min = ruleConfig.get("min");
        if (min != null) {
            rule.setMin(Double.parseDouble(String.valueOf(min)));
        }
        final Object max = ruleConfig.get("max");
        if (max != null) {
            rule.setMax(Double.parseDouble(String.valueOf(max)));
        }
        setString(ruleConfig, "level", rule::setLevel);
        return rule;
    }

    private Properties buildProperties(final Map<String, ?> config) {
        final Properties properties = new Properties();
        config.forEach((key, value) -> properties.put(key, value));
        final Yaml yaml = new Yaml();
        for (String key : new ArrayList<>(properties.stringPropertyNames())) {
            YamlConfigLoaderUtils.replacePropertyAndLog(
                key, properties.get(key), properties, "ai-evaluation", yaml);
        }
        return properties;
    }

    private EvaluationTask buildTask(final Map<String, ?> taskConfig) {
        final EvaluationTask task = new EvaluationTask();
        setString(taskConfig, "name", task::setName);
        setString(taskConfig, "instruction", task::setInstruction);

        final Object valueType = taskConfig.get("valueType");
        if (valueType != null) {
            task.setValueType(ValueType.valueOf(String.valueOf(valueType)));
        }

        final Object allowedValues = taskConfig.get("allowedValues");
        if (allowedValues instanceof List) {
            task.setAllowedValues((List<String>) allowedValues);
        }
        return task;
    }

    private void setString(final Map<String, ?> config,
                           final String key,
                           final Consumer<String> setter) {
        final Object value = config.get(key);
        if (value != null) {
            setter.accept(String.valueOf(value));
        }
    }
}
