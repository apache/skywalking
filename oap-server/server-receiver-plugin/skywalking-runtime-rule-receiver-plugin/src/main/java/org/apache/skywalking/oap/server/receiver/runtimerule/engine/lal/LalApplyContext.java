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

package org.apache.skywalking.oap.server.receiver.runtimerule.engine.lal;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyContext;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyInputs;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;

/**
 * LAL-specific {@link ApplyContext} marker. The engine's {@code Applied} artefact lives on
 * {@link AppliedRuleScript#getApplied} (cast to {@code LalFileApplier.Applied}), so this
 * context no longer needs a parallel applied map.
 */
@Getter
public final class LalApplyContext implements ApplyContext {
    private final ModuleManager moduleManager;
    private final StorageManipulationOpt storageOpt;
    private final Consumer<Set<String>> alarmResetter;
    private final Map<String, AppliedRuleScript> rules;

    public LalApplyContext(final ApplyInputs inputs) {
        this.moduleManager = inputs.getModuleManager();
        this.storageOpt = inputs.getStorageOpt();
        this.alarmResetter = inputs.getAlarmResetter();
        this.rules = inputs.getRules();
    }
}
