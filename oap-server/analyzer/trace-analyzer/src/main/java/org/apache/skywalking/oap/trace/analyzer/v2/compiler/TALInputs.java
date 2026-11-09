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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALConstruct;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALSourceTypeProvider;

/**
 * The inputs on the class path and the constructs each declares. The compiler checks every construct a
 * rule uses against its input's, and names the inputs a misused one belongs to, as {@link TALFunctionRegistry} does
 * for a function.
 */
public final class TALInputs {
    private static final Map<String, TALSourceTypeProvider> PROVIDERS = discover();
    private static final Map<String, Set<TALConstruct>> CONSTRUCTS = constructsOf(PROVIDERS);

    private TALInputs() {
    }

    private static Map<String, TALSourceTypeProvider> discover() {
        final Map<String, TALSourceTypeProvider> byAlias = new LinkedHashMap<>();
        for (final TALSourceTypeProvider provider : ServiceLoader.load(TALSourceTypeProvider.class)) {
            byAlias.put(provider.alias(), provider);
        }
        return byAlias;
    }

    private static Map<String, Set<TALConstruct>> constructsOf(final Map<String, TALSourceTypeProvider> providers) {
        final Map<String, Set<TALConstruct>> byAlias = new LinkedHashMap<>();
        providers.forEach((alias, provider) -> byAlias.put(alias, provider.constructs()));
        return byAlias;
    }

    /**
     * @return the inputs on the class path, which {@link TALFunctionRegistry#defaults()} binds
     */
    public static Collection<TALSourceTypeProvider> providers() {
        return PROVIDERS.values();
    }

    /**
     * @param construct  a construct
     * @param inputAlias the alias of a rule's input, the OTLP input's constructs for one the class path does not
     *                   register
     * @return whether that input has the construct
     */
    public static boolean has(final TALConstruct construct, final String inputAlias) {
        return CONSTRUCTS.getOrDefault(inputAlias, TALConstruct.SPAN_METADATA).contains(construct);
    }

    /**
     * @param construct  a construct the rule uses
     * @param inputAlias the alias of the rule's input; an input the class path does not register, as a unit test
     *                   declares, has the OTLP input's constructs
     * @throws IllegalArgumentException when the rule's input does not have the construct, naming the inputs that do
     */
    public static void require(final TALConstruct construct, final String inputAlias) {
        if (has(construct, inputAlias)) {
            return;
        }
        final Set<String> owners = new TreeSet<>();
        for (final Map.Entry<String, Set<TALConstruct>> input : CONSTRUCTS.entrySet()) {
            if (input.getValue().contains(construct)) {
                owners.add(input.getKey());
            }
        }
        throw new IllegalArgumentException(
            construct.spelling() + " belongs to " + (owners.isEmpty() ? "no registered input" : "the inputs " + owners)
                + ", not to this rule's inputType " + inputAlias);
    }
}
