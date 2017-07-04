package org.skywalking.apm.agent.core.context.trace;

import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.skywalking.apm.network.trace.component.Component;

/**
 * The <code>EntrySpan</code> represents a service provider point, such as Tomcat server entrance.
 *
 * It is a start point of {@link TraceSegment}, even in a complex application, there maybe have multi entry point,
 * the <code>EntrySpan</code> only represents the first one.
 *
 * But with the last <code>EntrySpan</code>'s tags and logs, which have more details about a service provider.
 *
 * @author wusheng
 */
public class EntrySpan extends AbstractTracingSpan {
    private int stackDepth;
    private int currentMaxDepth;

    public EntrySpan(int spanId, int parentSpanId, String operationName) {
        super(spanId, parentSpanId, operationName);
        this.stackDepth = 0;
        this.currentMaxDepth = 0;
    }

    public EntrySpan(int spanId, int parentSpanId, int operationId) {
        super(spanId, parentSpanId, operationId);
        this.stackDepth = 0;
        this.currentMaxDepth = 0;
    }

    /**
     * Set the {@link #startTime}, when the first start, which means the first service provided.
     */
    @Override
    public EntrySpan start() {
        if ((currentMaxDepth = ++stackDepth) == 1) {
            super.start();
        }
        clearWhenRestart();
        return this;
    }

    @Override
    public EntrySpan tag(String key, String value) {
        if (stackDepth == currentMaxDepth) {
            super.tag(key, value);
        }
        return this;
    }

    @Override
    public AbstractSpan setLayer(SpanLayer layer) {
        if (stackDepth == currentMaxDepth) {
            return super.setLayer(layer);
        } else {
            return this;
        }
    }

    @Override
    public AbstractSpan setComponent(Component component) {
        if (stackDepth == currentMaxDepth) {
            return super.setComponent(component);
        } else {
            return this;
        }
    }

    @Override
    public AbstractSpan setComponent(String componentName) {
        if (stackDepth == currentMaxDepth) {
            return super.setComponent(componentName);
        } else {
            return this;
        }
    }

    @Override
    public boolean finish(TraceSegment owner) {
        if (--stackDepth == 0) {
            return super.finish(owner);
        } else {
            return false;
        }
    }

    @Override
    public EntrySpan log(Throwable t) {
        super.log(t);
        return this;
    }

    @Override public boolean isEntry() {
        return true;
    }

    @Override public boolean isLocal() {
        return false;
    }

    @Override public boolean isExit() {
        return false;
    }

    private void clearWhenRestart() {
        this.componentId = DictionaryUtil.nullValue();
        this.componentName = null;
        this.layer = null;
        this.logs = null;
        this.tags = null;
    }
}
