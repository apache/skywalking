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
