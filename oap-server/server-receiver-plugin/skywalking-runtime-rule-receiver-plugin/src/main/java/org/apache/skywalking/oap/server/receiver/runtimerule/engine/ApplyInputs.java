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

package org.apache.skywalking.oap.server.receiver.runtimerule.engine;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;

/**
 * Shared scheduler inputs the orchestrator hands to {@link RuleEngine#newApplyContext} on
 * every apply / unregister call. The engine reads what it needs and folds it into its own
 * {@link ApplyContext} subtype together with whatever DSL-specific state the engine holds
 * internally (e.g. the MAL engine's {@code appliedMal} map).
 *
 * <p>Why a separate POJO instead of letting {@code RuleEngine.newApplyContext} take loose
 * parameters: signature stability. Adding a future shared service (a tracing context, a
 * feature-flag bag, etc.) is one field on this record without touching every engine.
 */
@Getter
@RequiredArgsConstructor
public final class ApplyInputs {
    private final ModuleManager moduleManager;
    private final StorageManipulationOpt storageOpt;
    private final Consumer<Set<String>> alarmResetter;
    private final Map<String, AppliedRuleScript> rules;
}
