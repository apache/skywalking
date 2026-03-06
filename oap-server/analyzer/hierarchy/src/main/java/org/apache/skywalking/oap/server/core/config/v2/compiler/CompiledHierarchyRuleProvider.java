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

package org.apache.skywalking.oap.server.core.config.v2.compiler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.config.HierarchyDefinitionService;
import org.apache.skywalking.oap.server.core.query.type.Service;

/**
 * SPI implementation of {@link HierarchyDefinitionService.HierarchyRuleProvider}
 * that compiles hierarchy matching rule expressions using ANTLR4 + Javassist.
 *
 * <p>Discovered at startup via {@code ServiceLoader} by
 * {@link HierarchyDefinitionService}. For each rule expression
 * (e.g., {@code "{ (u, l) -> u.name == l.name }"}):
 * <ol>
 *   <li>{@link HierarchyRuleClassGenerator#compile} parses the expression
 *       with ANTLR4 into an AST, then generates a Java class implementing
 *       {@code BiFunction<Service, Service, Boolean>} via Javassist.</li>
 *   <li>The generated class casts both arguments to {@link Service},
 *       evaluates the expression body, and returns a {@code Boolean}.</li>
 * </ol>
 *
 * <p>The compiled matchers are returned to {@link HierarchyDefinitionService}
 * and used at runtime by
 * {@link org.apache.skywalking.oap.server.core.hierarchy.HierarchyService}
 * to match service pairs.
 */
@Slf4j
public class CompiledHierarchyRuleProvider implements HierarchyDefinitionService.HierarchyRuleProvider {

    private final HierarchyRuleClassGenerator generator;

    public CompiledHierarchyRuleProvider() {
        generator = new HierarchyRuleClassGenerator();
        generator.setYamlSource("hierarchy-definition.yml");
    }

    @Override
    public Map<String, BiFunction<Service, Service, Boolean>> buildRules(
            final Map<String, String> ruleExpressions) {
        final Map<String, BiFunction<Service, Service, Boolean>> rules = new HashMap<>();
        ruleExpressions.forEach((name, expression) -> {
            try {
                rules.put(name, generator.compile(name, expression));
                log.debug("Compiled hierarchy rule: {}", name);
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Failed to compile hierarchy rule: " + name
                        + ", expression: " + expression, e);
            }
        });
        return rules;
    }
}
