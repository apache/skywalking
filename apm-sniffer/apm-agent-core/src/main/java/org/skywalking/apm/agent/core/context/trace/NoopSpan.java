package org.skywalking.apm.agent.core.context.trace;

import org.skywalking.apm.agent.core.context.IgnoredTracerContext;

/**
 * The <code>NoopSpan</code> represents a span implementation without any actual operation.
 * This span implementation is for {@link IgnoredTracerContext}.
 *
 * @author wusheng
 */
public class NoopSpan extends AbstractSpan {
    public NoopSpan() {
        super(null);
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public AbstractSpan log(Throwable t) {
        return super.log(t);
    }

    public void finish(){

    }

    @Override
    public AbstractSpan tag(String key, String value) {
        return null;
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
