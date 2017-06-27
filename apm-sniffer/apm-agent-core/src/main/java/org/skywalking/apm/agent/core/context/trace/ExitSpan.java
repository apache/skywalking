package org.skywalking.apm.agent.core.context.trace;

/**
 * The <code>ExitSpan</code> represents a service consumer point, such as Feign, Okhttp client for a Http service.
 *
 * It is an exit point or a leaf span(our old name) of trace tree.
 * In a single rpc call, because of a combination of client libs, there maybe contain multi exit point.
 *
 * The <code>ExitSpan</code> only presents the first one.
 *
 * @author wusheng
 */
public class ExitSpan extends AbstractTracingSpan {
    private int stackDepth;
    private String peerPoint;

    public ExitSpan(int spanId, int parentSpanId, String operationName, String peerPoint) {
        super(spanId, parentSpanId, operationName);
        this.stackDepth = 0;
        this.peerPoint = peerPoint;
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
        }else{
            return false;
        }
    }

    @Override
    public ExitSpan log(Throwable t) {
        if (stackDepth == 1) {
            super.log(t);
        }
        return this;
    }

    @Override public boolean isEntry() {
        return false;
    }

    @Override public boolean isLocal() {
        return false;
    }

    @Override public boolean isExit() {
        return true;
    }
}
