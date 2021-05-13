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

package org.apache.skywalking.apm.plugin.vertx3;

import io.vertx.core.impl.launcher.commands.VersionCommand;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

class VertxContext {

    public static final double VERTX_VERSION;

    static {
        double version;
        try {
            version = Double.parseDouble(VersionCommand.getVersion().replaceFirst("\\.", ""));
        } catch (Throwable ignored) {
            version = 3.00;
        }
        VERTX_VERSION = version;
    }

    public static final String STOP_SPAN_NECESSARY = "VERTX_STOP_SPAN_NECESSARY";
    private static final Map<String, Deque<VertxContext>> CONTEXT_MAP = new ConcurrentHashMap<>();

    static void pushContext(String identifier, VertxContext vertxContext) {
        if (!CONTEXT_MAP.containsKey(identifier)) {
            CONTEXT_MAP.put(identifier, new LinkedBlockingDeque<>());
        }
        CONTEXT_MAP.get(identifier).push(vertxContext);
    }

    static VertxContext popContext(String identifier) {
        final Deque<VertxContext> stack = CONTEXT_MAP.get(identifier);
        final VertxContext context = stack.pop();
        if (stack.isEmpty()) {
            CONTEXT_MAP.remove(identifier);
        }
        return context;
    }

    static VertxContext peekContext(String identifier) {
        return CONTEXT_MAP.get(identifier).peek();
    }

    static boolean hasContext(String identifier) {
        return identifier != null && CONTEXT_MAP.containsKey(identifier);
    }

    private final ContextSnapshot contextSnapshot;
    private final AbstractSpan span;

    VertxContext(ContextSnapshot contextSnapshot, AbstractSpan span) {
        this.contextSnapshot = contextSnapshot;
        this.span = span;
    }

    ContextSnapshot getContextSnapshot() {
        return contextSnapshot;
    }

    AbstractSpan getSpan() {
        return span;
    }
}
