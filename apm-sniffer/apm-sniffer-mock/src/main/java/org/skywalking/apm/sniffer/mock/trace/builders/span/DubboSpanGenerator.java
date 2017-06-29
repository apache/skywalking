package org.skywalking.apm.sniffer.mock.trace.builders.span;

import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;

/**
 * The <code>DubboSpanGenerator</code> generates all possible spans, by tracing Dubbo rpc.
 * Including discovery/server side span.
 *
 * @author wusheng
 */
public class DubboSpanGenerator {
    public static class Client extends SpanGeneration {
        @Override
        protected void before() {
            AbstractSpan span = ContextManager.createSpan("/default_rpc/org.skywalking.apm.test.persistence.PersistenceService.query");
            Tags.COMPONENT.set(span, "Dubbo");
            Tags.URL.set(span, "rest://192.168.1.8:20880/default_rpc/org.skywalking.apm.test.persistence.PersistenceService.query(String)");
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);
            span.setPeerHost("192.168.1.8");
            span.setPort(20880);
            Tags.SPAN_LAYER.asHttp(span);
        }

        @Override
        protected void after() {
            ContextManager.stopSpan();
        }
    }

    public static class Server extends SpanGeneration {
        @Override
        protected void before() {
            AbstractSpan span = ContextManager.createSpan("/default_rpc/org.skywalking.apm.test.persistence.PersistenceService.query");
            Tags.COMPONENT.set(span, "Dubbo");
            Tags.URL.set(span, "rest://192.168.1.8:20880/default_rpc/org.skywalking.apm.test.persistence.PersistenceService.query(String)");
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
            span.setPeerHost("10.21.9.35");
            Tags.SPAN_LAYER.asHttp(span);
        }

        @Override
        protected void after() {
            ContextManager.stopSpan();
        }
    }
}
