package org.skywalking.apm.agent.core.context.trace;

import org.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.skywalking.apm.network.trace.component.Component;

/**
 * The <code>NoopSpan</code> represents a span implementation without any actual operation.
 * This span implementation is for {@link IgnoredTracerContext},
 * for keeping the memory and gc cost as low as possible.
 *
 * @author wusheng
 */
public class NoopSpan implements AbstractSpan {
    public NoopSpan() {
    }

    @Override
    public AbstractSpan log(Throwable t) {
        return this;
    }

    @Override public AbstractSpan errorOccurred() {
        return null;
    }

    public void finish() {

    }

    @Override public AbstractSpan setComponent(Component component) {
        return this;
    }

    @Override public AbstractSpan setComponent(String componentName) {
        return this;
    }

    @Override public AbstractSpan setLayer(SpanLayer layer) {
        return this;
    }

    @Override
    public AbstractSpan tag(String key, String value) {
        return this;
    }

    @Override public boolean isEntry() {
        return false;
    }

    @Override public boolean isLocal() {
        return false;
    }

    @Override public boolean isExit() {
        return false;
    }
}
