package org.skywalking.apm.agent.core.context.trace;

import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.trace.component.Component;

/**
 * The <code>ExitSpan</code> represents a service consumer point, such as Feign, Okhttp client for a Http service.
 *
 * It is an exit point or a leaf span(our old name) of trace tree.
 * In a single rpc call, because of a combination of discovery libs, there maybe contain multi-layer exit point:
 *
 * The <code>ExitSpan</code> only presents the first one.
 *
 * Such as: Dubbox -> Apache Httpcomponent -> ....(Remote)
 * The <code>ExitSpan</code> represents the Dubbox span, and ignore the httpcomponent span's info.
 *
 * @author wusheng
 */
public class ExitSpan extends AbstractTracingSpan {
    private int stackDepth;
    private String peer;
    private int peerId;

    public ExitSpan(int spanId, int parentSpanId, String operationName, String peer) {
        super(spanId, parentSpanId, operationName);
        this.stackDepth = 0;
        this.peer = peer;
        this.peerId = DictionaryUtil.nullValue();
    }

    public ExitSpan(int spanId, int parentSpanId, int operationId, int peerId) {
        super(spanId, parentSpanId, operationId);
        this.stackDepth = 0;
        this.peer = null;
        this.peerId = peerId;
    }

    public ExitSpan(int spanId, int parentSpanId, int operationId, String peer) {
        super(spanId, parentSpanId, operationId);
        this.stackDepth = 0;
        this.peer = peer;
        this.peerId = DictionaryUtil.nullValue();
    }

    /**
     * Set the {@link #startTime}, when the first start, which means the first service provided.
     */
    @Override
    public ExitSpan start() {
        if (++stackDepth == 1) {
            super.start();
        }
        return this;
    }

    @Override
    public ExitSpan tag(String key, String value) {
        if (stackDepth == 1) {
            super.tag(key, value);
        }
        return this;
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
    public AbstractSpan setLayer(SpanLayer layer) {
        if (stackDepth == 1) {
            return super.setLayer(layer);
        } else {
            return this;
        }
    }

    @Override
    public AbstractSpan setComponent(Component component) {
        if (stackDepth == 1) {
            return super.setComponent(component);
        } else {
            return this;
        }
    }

    @Override
    public AbstractSpan setComponent(String componentName) {
        if (stackDepth == 1) {
            return super.setComponent(componentName);
        } else {
            return this;
        }
    }

    @Override
    public ExitSpan log(Throwable t) {
        if (stackDepth == 1) {
            super.log(t);
        }
        return this;
    }

    @Override public SpanObject.Builder transform() {
        SpanObject.Builder spanBuilder = super.transform();
        if (peerId == DictionaryUtil.nullValue()) {
            spanBuilder.setPeerId(peerId);
        } else {
            spanBuilder.setPeer(peer);
        }
        return spanBuilder;
    }

    public int getPeerId() {
        return peerId;
    }

    public String getPeer() {
        return peer;
    }

    @Override public boolean isEntry() {
        return false;
    }

    @Override public boolean isExit() {
        return true;
    }
}
