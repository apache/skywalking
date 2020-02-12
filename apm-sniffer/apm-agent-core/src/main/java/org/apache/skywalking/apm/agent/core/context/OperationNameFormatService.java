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

package org.apache.skywalking.apm.agent.core.context;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.OPGroupDefinition;
import org.apache.skywalking.apm.util.StringFormatGroup;

/**
 * Support operation name format by config. Every plugin could declare its own rule to avoid performance concerns.
 * <p>
 * Right now, the rule is REGEX based, it definitely has much space to optimize, because basically, only `*` is required
 * to be supported.
 */
@DefaultImplementor
public class OperationNameFormatService implements BootService {
    private static final Map<Class, StringFormatGroup> RULES = new ConcurrentHashMap<Class, StringFormatGroup>();

    @Override
    public void prepare() throws Throwable {
        for (Class<?> ruleName : Config.Plugin.OPGroup.class.getClasses()) {
            if (!OPGroupDefinition.class.isAssignableFrom(ruleName)) {
                continue;
            }
            StringFormatGroup formatGroup = RULES.get(ruleName);
            if (formatGroup == null) {
                formatGroup = new StringFormatGroup();
                RULES.put(ruleName, formatGroup);
            }
            for (Field ruleNameField : ruleName.getFields()) {
                if (ruleNameField.getType().equals(Map.class)) {
                    Map<String, String> rule = (Map<String, String>) ruleNameField.get(null);
                    for (Map.Entry<String, String> entry : rule.entrySet()) {
                        formatGroup.addRule(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }

    @Override
    public void boot() {

    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {

    }

    /**
     * Format the operation name based on group rules
     *
     * @param definition in the Config
     * @param opName     represents the operation name literal string
     * @return format string if rule matched or the given opName
     */
    public String formatOperationName(Class<? extends OPGroupDefinition> definition, String opName) {
        StringFormatGroup formatGroup = RULES.get(definition);
        if (formatGroup == null) {
            return opName;
        } else {
            return formatGroup.format(opName).getName();
        }
    }
}
