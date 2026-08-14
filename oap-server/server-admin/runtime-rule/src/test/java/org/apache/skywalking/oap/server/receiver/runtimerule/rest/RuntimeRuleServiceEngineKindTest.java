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

package org.apache.skywalking.oap.server.receiver.runtimerule.rest;

import java.util.HashMap;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngineRegistry;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.lal.LalRuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.mal.MalRuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins which engine classifies a catalog's rule content.
 *
 * <p>This was a MAL/LAL ternary — {@code isMalCatalog(...) ? classifyMal : classifyLal} — written
 * when MAL and LAL were the only two engines, so its {@code else} genuinely meant LAL. It would
 * therefore hand a THIRD engine's rule to the LAL classifier.
 *
 * <p>Scope, so these assertions are not read as more than they are: on the REST path an
 * unregistered catalog never reaches this method. {@code RuntimeRuleService.validate} rejects it
 * with {@code invalid_catalog} using the very same registry lookup. {@code engineKindFor} is a
 * pure function and its {@code null} behaviour is pinned below as a property of the function, not
 * as a reachable request. What is genuinely reachable, once a third engine registers, is the last
 * case: an engine this dispatch does not know.
 */
class RuntimeRuleServiceEngineKindTest {

    private static RuleEngineRegistry registryWith(final boolean mal, final boolean lal) {
        final RuleEngineRegistry registry = new RuleEngineRegistry();
        // Both constructors only assign their two fields, so a null ModuleManager is safe here:
        // engineKindFor never calls into the engine, it only asks the registry which type owns
        // the catalog.
        if (mal) {
            registry.register(new MalRuleEngine(new HashMap<String, AppliedRuleScript>(), null));
        }
        if (lal) {
            registry.register(new LalRuleEngine(new HashMap<String, AppliedRuleScript>(), null));
        }
        return registry;
    }

    @Test
    void aMalCatalogResolvesToMal() {
        final RuleEngineRegistry registry = registryWith(true, true);

        assertEquals(RuntimeRuleService.EngineKind.MAL,
            RuntimeRuleService.engineKindFor(registry, "otel-rules"));
        // Every catalog MalRuleEngine declares must land on MAL, not just the first — that is the
        // property the registry lookup exists to preserve as catalogs are added.
        assertEquals(RuntimeRuleService.EngineKind.MAL,
            RuntimeRuleService.engineKindFor(registry, "meter-analyzer-config"));
        assertEquals(RuntimeRuleService.EngineKind.MAL,
            RuntimeRuleService.engineKindFor(registry, "telegraf-rules"));
        assertEquals(RuntimeRuleService.EngineKind.MAL,
            RuntimeRuleService.engineKindFor(registry, "log-mal-rules"));
    }

    @Test
    void aLalCatalogResolvesToLal() {
        assertEquals(RuntimeRuleService.EngineKind.LAL,
            RuntimeRuleService.engineKindFor(registryWith(true, true), "lal"));
    }

    @Test
    void anUnregisteredCatalogIsNoneRatherThanLal() {
        // A property of the function, not a reachable request: validate() rejects an unregistered
        // catalog before the dispatch runs. Pinned so the dispatch stays total.
        assertEquals(RuntimeRuleService.EngineKind.NONE,
            RuntimeRuleService.engineKindFor(registryWith(true, true), "no-such-catalog"));
        assertEquals(RuntimeRuleService.EngineKind.NONE,
            RuntimeRuleService.engineKindFor(registryWith(true, true), ""));
    }

    @Test
    void anEngineThisDispatchDoesNotKnowIsNoneRatherThanLal() {
        // The case that is actually reachable, once a third RuleEngine registers: its catalog
        // passes validate() because forCatalog returns non-null, then matches neither instanceof.
        // With the old ternary it would have been classified by the LAL parser, silently. The
        // registry here stands in for that: an engine registered for one DSL, asked about the
        // other's catalog.
        assertEquals(RuntimeRuleService.EngineKind.NONE,
            RuntimeRuleService.engineKindFor(registryWith(true, false), "lal"));
        assertEquals(RuntimeRuleService.EngineKind.NONE,
            RuntimeRuleService.engineKindFor(registryWith(false, true), "otel-rules"));
    }
}
