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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shape of every metric the loaded TAL rules declare, across rules and files, so two rules feeding one metric
 * name with different shapes fail when they load. Several blocks may feed one metric, from one rule or several, as
 * long as they agree; the meter system keeps the first definition of a name and only checks its function, scope
 * and data type, so a disagreement in buckets, ranks or label limit would otherwise merge two layouts into one
 * stored series.
 */
public final class TALMetricShapes {
    private final Map<String, Claim> byName = new HashMap<>();

    /**
     * Record what one rule declares, refusing a metric another rule declares differently. A rule loaded again
     * replaces its own claims.
     *
     * @param rule         the rule, named so the error can point at it
     * @param declarations what the rule declares
     */
    public synchronized void claim(final String rule, final List<TALMetricDeclaration> declarations) {
        for (final TALMetricDeclaration declaration : declarations) {
            final Claim previous = byName.get(declaration.fullName);
            if (previous != null && !previous.rule.equals(rule) && !previous.declaration.equals(declaration)) {
                throw new IllegalArgumentException(
                    "metric " + declaration.fullName + " is declared as " + previous.declaration + " by rule "
                        + previous.rule + " and as " + declaration + " by rule " + rule);
            }
        }
        for (final TALMetricDeclaration declaration : declarations) {
            byName.put(declaration.fullName, new Claim(rule, declaration));
        }
    }

    private static final class Claim {
        private final String rule;
        private final TALMetricDeclaration declaration;

        private Claim(final String rule, final TALMetricDeclaration declaration) {
            this.rule = rule;
            this.declaration = declaration;
        }
    }
}
