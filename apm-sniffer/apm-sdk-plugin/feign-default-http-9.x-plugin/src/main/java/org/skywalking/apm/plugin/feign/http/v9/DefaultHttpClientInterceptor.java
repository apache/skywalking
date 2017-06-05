package org.skywalking.apm.plugin.feign.http.v9;

import feign.Request;
import feign.Response;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.tag.Tags;

/**
 * {@link DefaultHttpClientInterceptor} intercept the default implementation of http calls by the Feign.
 *
 * @author pengys5
 */
public class DefaultHttpClientInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String COMPONENT_NAME = "FeignDefaultHttp";

    /**
     * Get the {@link feign.Request} from {@link EnhancedClassInstanceContext}, then create {@link Span} and set host,
     * port, kind, component, url from {@link feign.Request}.
     * Through the reflection of the way, set the http header of context data into {@link feign.Request#headers}.
     *
     * @param context instance context, a class instance only has one {@link EnhancedClassInstanceContext} instance.
     * @param interceptorContext method context, includes class name, method name, etc.
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        MethodInterceptResult result) throws Throwable {
        Request request = (Request)interceptorContext.allArguments()[0];

        URL url = new URL(request.url());
        Span span = ContextManager.createSpan(request.url());
        Tags.PEER_PORT.set(span, url.getPort());
        Tags.PEER_HOST.set(span, url.getHost());
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
        Tags.COMPONENT.set(span, COMPONENT_NAME);
        Tags.HTTP.METHOD.set(span, request.method());
        Tags.URL.set(span, url.getPath());
        Tags.SPAN_LAYER.asHttp(span);

        ContextCarrier contextCarrier = new ContextCarrier();
        ContextManager.inject(contextCarrier);

        List<String> contextCollection = new ArrayList<String>();
        contextCollection.add(contextCarrier.serialize());

        Field headersField = Request.class.getDeclaredField("headers");
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(headersField, headersField.getModifiers() & ~Modifier.FINAL);

        headersField.setAccessible(true);
        Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
        headers.put(Config.Plugin.Http.HEADER_NAME_OF_CONTEXT_DATA, contextCollection);
        headers.putAll(request.headers());

        headersField.set(request, Collections.unmodifiableMap(headers));
    }

    /**
     * Get the status code from {@link Response}, when status code greater than 400, it means there was some errors in
     * the server.
     * Finish the {@link Span}.
     *
     * @param context instance context, a class instance only has one {@link EnhancedClassInstanceContext} instance.
     * @param interceptorContext method context, includes class name, method name, etc.
     * @param ret the method's original return value.
     * @return
     * @throws Throwable
     */
    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        Object ret) throws Throwable {
        Response response = (Response)ret;
        int statusCode = response.status();

        Span span = ContextManager.activeSpan();
        if (statusCode >= 400) {
            Tags.ERROR.set(span, true);
        }

        Tags.STATUS_CODE.set(span, statusCode);
        ContextManager.stopSpan();

        return ret;
    }

    @Override public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
        InstanceMethodInvokeContext interceptorContext) {
        Tags.ERROR.set(ContextManager.activeSpan(), true);
        ContextManager.activeSpan().log(t);
    }
}
