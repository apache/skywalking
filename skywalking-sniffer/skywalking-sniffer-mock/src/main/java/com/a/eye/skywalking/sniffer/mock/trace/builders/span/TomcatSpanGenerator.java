package com.a.eye.skywalking.sniffer.mock.trace.builders.span;

import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;

/**
 * The <code>TomcatSpanGenerator</code> generate all possible spans, by tracing Tomcat.
 *
 * Created by wusheng on 2017/2/20.
 */
public class TomcatSpanGenerator{
    public static class ON200 extends SpanGeneration{
        public static final ON200 INSTANCE = new ON200();

        @Override protected void before() {
            Span webSpan = ContextManager.INSTANCE.createSpan("/web/serviceA");
            Tags.COMPONENT.set(webSpan, "Tomcat");
            Tags.URL.set(webSpan, "http://10.21.9.35/web/serviceA");
            Tags.SPAN_KIND.set(webSpan, Tags.SPAN_KIND_SERVER);
            Tags.PEER_HOST.set(webSpan, "10.21.9.35");
            Tags.PEER_PORT.set(webSpan, 80);
            Tags.SPAN_LAYER.asHttp(webSpan);
        }

        @Override protected void after() {
            Span webSpan = ContextManager.INSTANCE.activeSpan();
            Tags.STATUS_CODE.set(webSpan, 200);
            ContextManager.INSTANCE.stopSpan();
        }
    }

    public static class ON404 extends SpanGeneration{
        public static final ON404 INSTANCE = new ON404();

        @Override protected void before() {
            Span webSpan = ContextManager.INSTANCE.createSpan("/web/service/unknown");
            Tags.COMPONENT.set(webSpan, "Tomcat");
            Tags.URL.set(webSpan, "http://10.21.9.35/web/unknown");
            Tags.SPAN_KIND.set(webSpan, Tags.SPAN_KIND_SERVER);
            Tags.PEER_HOST.set(webSpan, "10.21.9.35");
            Tags.PEER_PORT.set(webSpan, 80);
            Tags.SPAN_LAYER.asHttp(webSpan);
        }

        @Override protected void after() {
            Span webSpan = ContextManager.INSTANCE.activeSpan();
            Tags.STATUS_CODE.set(webSpan, 404);
            Tags.ERROR.set(webSpan,true);
            ContextManager.INSTANCE.stopSpan();
        }
    }
    public static class ON500 extends SpanGeneration{
        public static final ON500 INSTANCE = new ON500();

        @Override protected void before() {
            Span webSpan = ContextManager.INSTANCE.createSpan("/web/error/service");
            Tags.COMPONENT.set(webSpan, "Tomcat");
            Tags.URL.set(webSpan, "http://10.21.9.35/web/error/service");
            Tags.SPAN_KIND.set(webSpan, Tags.SPAN_KIND_SERVER);
            Tags.PEER_HOST.set(webSpan, "10.21.9.35");
            Tags.PEER_PORT.set(webSpan, 80);
            Tags.SPAN_LAYER.asHttp(webSpan);
        }

        @Override protected void after() {
            Span webSpan = ContextManager.INSTANCE.activeSpan();
            Tags.STATUS_CODE.set(webSpan, 500);
            Tags.ERROR.set(webSpan,true);
            webSpan.log(new NumberFormatException("Can't convert 'abc' to int."));
            ContextManager.INSTANCE.stopSpan();
        }
    }
}
