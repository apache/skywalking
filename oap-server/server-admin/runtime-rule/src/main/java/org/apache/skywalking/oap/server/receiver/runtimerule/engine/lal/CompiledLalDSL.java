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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.receiver.runtimerule.apply.LalFileApplier;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.Classification;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.CompiledDSL;

/**
 * LAL-specific {@link CompiledDSL} carrying the output of {@link LalRuleEngine#compile}
 * through {@link LalRuleEngine#commit} (or rollback). LAL has no backend schema, so the
 * fire / verify phases are no-ops; the only artifacts that flow phase-to-phase are the new
 * {@link LalFileApplier.Applied} (for the in-memory swap) and the prior one (so commit can
 * compute truly-gone keys + retire the displaced loader).
 */
@Getter
@RequiredArgsConstructor
public final class CompiledLalDSL implements CompiledDSL {
    private final String catalog;
    private final String name;
    private final String contentHash;
    private final Classification classification;
    /** Raw YAML the bundle was compiled from, written into {@code appliedContent[key]} on
     *  commit so the next classify call has the prior content to diff against. */
    private final String content;
    /** Prior bundle, {@code null} on first apply. */
    private final LalFileApplier.Applied oldApplied;
    /** Freshly-compiled bundle. Live in {@code LogFilterListener.Factory} from the moment
     *  compile returned via {@code addOrReplace} — rollback re-uses the partial registration
     *  set on the apply exception. */
    private final LalFileApplier.Applied newApplied;
}
