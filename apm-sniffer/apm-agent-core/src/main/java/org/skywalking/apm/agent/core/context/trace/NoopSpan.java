package org.skywalking.apm.agent.core.context.trace;

import java.util.Map;
import org.skywalking.apm.agent.core.context.IgnoredTracerContext;

/**
 * The <code>NoopSpan</code> represents a span implementation without any actual operation.
 * This span implementation is for {@link IgnoredTracerContext}.
 *
 * @author wusheng
 */
public class NoopSpan implements AbstractSpan {
    @Override
    public AbstractSpan setOperationName(String operationName) {
        return this;
    }

    @Override
    public void setPeerHost(String peerHost) {

    }

    @Override
    public void setPort(int port) {

    }

    @Override
    public void setPeers(String peers) {

    }

    @Override
    public AbstractSpan setTag(String key, String value) {
        return this;
    }

    @Override
    public AbstractSpan setTag(String key, boolean value) {
        return this;
    }

    @Override
    public AbstractSpan setTag(String key, Integer value) {
        return this;
    }

    @Override
    public AbstractSpan log(Map<String, String> fields) {
        return this;
    }

    @Override
    public AbstractSpan log(Throwable t) {
        return this;
    }

    @Override
    public AbstractSpan log(String event) {
        return this;
    }

}
