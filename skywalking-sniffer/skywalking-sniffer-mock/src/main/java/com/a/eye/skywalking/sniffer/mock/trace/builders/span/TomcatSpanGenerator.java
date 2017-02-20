package com.a.eye.skywalking.sniffer.mock.trace.builders.span;

import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;

/**
 * The <code>TomcatSpanGenerator</code> generate all possible spans, by tracing Tomcat.
 *
 * Created by wusheng on 2017/2/20.
 */
public enum TomcatSpanGenerator {
    INSTANCE;

    /**
     * When tomcat response 200.
     */
    public void on200(){
        Span webSpan = ContextManager.INSTANCE.createSpan("/web/serviceA");
        Tags.COMPONENT.set(webSpan, "tomcat");
        Tags.URL.set(webSpan, "http://10.21.9.35/web/serviceA");
        Tags.SPAN_KIND.set(webSpan, Tags.SPAN_KIND_SERVER);
        Tags.STATUS_CODE.set(webSpan, 200);
        ContextManager.INSTANCE.stopSpan(webSpan);
    }

    /**
     * When tomcat response 404.
     */
    public void on404(){
        Span webSpan = ContextManager.INSTANCE.createSpan("/web/service/unknown");
        Tags.COMPONENT.set(webSpan, "tomcat");
        Tags.URL.set(webSpan, "http://10.21.9.35/web/unknown");
        Tags.SPAN_KIND.set(webSpan, Tags.SPAN_KIND_SERVER);
        Tags.STATUS_CODE.set(webSpan, 404);
        Tags.ERROR.set(webSpan,true);
        ContextManager.INSTANCE.stopSpan(webSpan);
    }

    /**
     * When tomcat response 500.
     */
    public void on500(){
        Span webSpan = ContextManager.INSTANCE.createSpan("/web/error/service");
        Tags.COMPONENT.set(webSpan, "tomcat");
        Tags.URL.set(webSpan, "http://10.21.9.35/web/error/service");
        Tags.SPAN_KIND.set(webSpan, Tags.SPAN_KIND_SERVER);
        Tags.STATUS_CODE.set(webSpan, 500);
        Tags.ERROR.set(webSpan,true);
        webSpan.log(new NumberFormatException("Can't convert 'abc' to int."));
        ContextManager.INSTANCE.stopSpan(webSpan);
    }
}
