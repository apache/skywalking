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

package org.apache.skywalking.oap.server.admin.dsl.debugging.session;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;

/**
 * Per-session aggregate carried by the registry: identity, the recorder
 * appending payloads on probe call, and the {@link GateHolder} the install
 * path bound to. The bound holder is captured at install time and never
 * updated — uninstall removes the recorder from the <i>original</i> holder
 * so a hot-update of the rule (which produces a new holder on a new live
 * artifact) doesn't strand the old holder's recorder array.
 *
 * <p>Lifecycle: {@code install -> capture window -> stop | retention timeout},
 * always uninstalls on the bound holder. The registry owns the lifecycle —
 * this class is a state record, not a behaviour.
 */
@Getter
@RequiredArgsConstructor
public final class DebugSession {

    private final String sessionId;
    private final String clientId;
    private final RuleKey ruleKey;
    private final AbstractDebugRecorder recorder;
    private final GateHolder boundHolder;
    private final long createdAtMillis;
    private final long retentionDeadlineMillis;
}
