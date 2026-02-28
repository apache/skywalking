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

package org.apache.skywalking.oap.server.core.config;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.apache.skywalking.oap.server.core.query.type.Service;

/**
 * Groovy-based hierarchy rule provider. Uses GroovyShell.evaluate() to compile
 * hierarchy matching rule closures from YAML expressions.
 *
 * <p>This provider is NOT included in the runtime classpath. It is only used
 * by the hierarchy-v1-v2-checker module for CI validation against the pure Java
 * provider (hierarchy-v2).
 */
public final class GroovyHierarchyRuleProvider implements HierarchyDefinitionService.HierarchyRuleProvider {

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, BiFunction<Service, Service, Boolean>> buildRules(
            final Map<String, String> ruleExpressions) {
        final Map<String, BiFunction<Service, Service, Boolean>> rules = new HashMap<>();
        final GroovyShell sh = new GroovyShell();
        ruleExpressions.forEach((name, expression) -> {
            final Closure<Boolean> closure = (Closure<Boolean>) sh.evaluate(expression);
            rules.put(name, (u, l) -> closure.call(u, l));
        });
        return rules;
    }
}
