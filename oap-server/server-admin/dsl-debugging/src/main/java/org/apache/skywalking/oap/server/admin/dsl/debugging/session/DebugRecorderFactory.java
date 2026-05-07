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

import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;

/**
 * Per-DSL recorder factory consulted by the registry when a session is
 * installed. Each DSL ({@code MAL}, {@code LAL}, {@code OAL}) registers one
 * factory; the registry asks each {@link #serves(RuleKey)} until one matches,
 * then calls {@link #create(String, RuleKey, GateHolder, SessionLimits)} to
 * build the concrete recorder. Same DSL-agnostic registry shape as
 * {@link DebugHolderLookup}.
 *
 * <p>The {@code boundHolder} argument is the holder the registry resolved for
 * this rule key just before calling {@code create}. The recorder reads its
 * content hash at construction so every captured record can be stamped with
 * the version of the rule it was captured from.
 */
public interface DebugRecorderFactory {

    /** {@code true} if this factory builds recorders for the rule key's catalog. */
    boolean serves(RuleKey key);

    /**
     * Build a fresh recorder for one debug session. The returned recorder is
     * the implementation type each DSL's probe class downcasts to.
     */
    AbstractDebugRecorder create(String sessionId, RuleKey key,
                                 GateHolder boundHolder, SessionLimits limits);
}
