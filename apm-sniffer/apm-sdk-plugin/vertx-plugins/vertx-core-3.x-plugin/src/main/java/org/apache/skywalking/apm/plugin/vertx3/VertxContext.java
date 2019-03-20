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

import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author brandon.fergerson
 */
class VertxContext {

    private static final Map<String, Stack<VertxContext>> CONTEXT_MAP = new ConcurrentHashMap<String, Stack<VertxContext>>();

    static void pushContext(String identifier, VertxContext vertxContext) {
        if (!CONTEXT_MAP.containsKey(identifier)) {
            CONTEXT_MAP.put(identifier, new Stack<VertxContext>());
        }
        CONTEXT_MAP.get(identifier).push(vertxContext);
    }

    static VertxContext popContext(String identifier) {
        return CONTEXT_MAP.get(identifier).pop();
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
