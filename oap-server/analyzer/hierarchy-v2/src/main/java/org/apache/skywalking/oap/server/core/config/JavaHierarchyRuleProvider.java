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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import org.apache.skywalking.oap.server.core.query.type.Service;

/**
 * Pure Java hierarchy rule provider. Contains a static registry of all known
 * hierarchy matching rules as Java lambdas. Zero Groovy dependency.
 *
 * <p>Rule names must match those in hierarchy-definition.yml auto-matching-rules section.
 * Unknown rule names fail fast at startup with IllegalArgumentException.
 */
public final class JavaHierarchyRuleProvider implements HierarchyDefinitionService.HierarchyRuleProvider {

    private static final Map<String, BiFunction<Service, Service, Boolean>> RULE_REGISTRY;

    static {
        RULE_REGISTRY = new HashMap<>();

        // name: { (u, l) -> u.name == l.name }
        RULE_REGISTRY.put("name",
            (u, l) -> Objects.equals(u.getName(), l.getName()));

        // short-name: { (u, l) -> u.shortName == l.shortName }
        RULE_REGISTRY.put("short-name",
            (u, l) -> Objects.equals(u.getShortName(), l.getShortName()));

        // lower-short-name-remove-ns:
        // { (u, l) -> { if(l.shortName.lastIndexOf('.') > 0)
        //     return u.shortName == l.shortName.substring(0, l.shortName.lastIndexOf('.'));
        //     return false; } }
        RULE_REGISTRY.put("lower-short-name-remove-ns", (u, l) -> {
            final String sn = l.getShortName();
            final int dot = sn.lastIndexOf('.');
            return dot > 0 && Objects.equals(u.getShortName(), sn.substring(0, dot));
        });

        // lower-short-name-with-fqdn:
        // { (u, l) -> { if(u.shortName.lastIndexOf(':') > 0)
        //     return u.shortName.substring(0, u.shortName.lastIndexOf(':')) == l.shortName.concat('.svc.cluster.local');
        //     return false; } }
        RULE_REGISTRY.put("lower-short-name-with-fqdn", (u, l) -> {
            final String sn = u.getShortName();
            final int colon = sn.lastIndexOf(':');
            return colon > 0 && Objects.equals(
                sn.substring(0, colon),
                l.getShortName() + ".svc.cluster.local");
        });
    }

    @Override
    public Map<String, BiFunction<Service, Service, Boolean>> buildRules(
            final Map<String, String> ruleExpressions) {
        final Map<String, BiFunction<Service, Service, Boolean>> rules = new HashMap<>();
        ruleExpressions.forEach((name, expression) -> {
            final BiFunction<Service, Service, Boolean> fn = RULE_REGISTRY.get(name);
            if (fn == null) {
                throw new IllegalArgumentException(
                    "Unknown hierarchy matching rule: " + name
                        + ". Known rules: " + RULE_REGISTRY.keySet());
            }
            rules.put(name, fn);
        });
        return rules;
    }
}
