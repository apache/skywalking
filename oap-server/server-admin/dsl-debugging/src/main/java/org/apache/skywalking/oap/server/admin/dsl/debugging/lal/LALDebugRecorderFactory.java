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

package org.apache.skywalking.oap.server.admin.dsl.debugging.lal;

import org.apache.skywalking.oap.server.admin.dsl.debugging.session.AbstractDebugRecorder;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugRecorderFactory;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.Granularity;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.SessionLimits;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;

/**
 * Builds {@link LALDebugRecorderImpl} for any {@link RuleKey} whose catalog
 * is {@link Catalog#LAL}. Statement-mode is opt-in via {@code limits.granularity};
 * block-mode short-circuits {@code captureLine} on the recorder side so the
 * unconditional codegen call site stays cheap when the operator hasn't asked
 * for per-line detail.
 */
public final class LALDebugRecorderFactory implements DebugRecorderFactory {

    @Override
    public boolean serves(final RuleKey key) {
        return key.getCatalog() == Catalog.LAL;
    }

    @Override
    public AbstractDebugRecorder create(final String sessionId, final RuleKey key,
                                        final GateHolder boundHolder, final SessionLimits limits) {
        final boolean statement = limits.getGranularity() == Granularity.STATEMENT;
        return new LALDebugRecorderImpl(sessionId, key, boundHolder, limits, statement);
    }
}
