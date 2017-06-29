package org.skywalking.apm.plugin.okhttp.v3;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * {@link RealCallInterceptor} intercept the synchronous http calls by the discovery of okhttp.
 *
 * @author pengys5
 */
public class RealCallInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    private static final String COMPONENT_NAME = "OKHttp";

    private static final String REQUEST_CONTEXT_KEY = "SWRequestContextKey";

    /**
     * Intercept the {@link okhttp3.RealCall#RealCall(OkHttpClient, Request, boolean)}, then put the second argument of
     * {@link okhttp3.Request} into {@link EnhancedClassInstanceContext} with the key of {@link
     * RealCallInterceptor#REQUEST_CONTEXT_KEY}.
     *
     * @param context a new added instance field
     * @param interceptorContext constructor invocation context.
     */
    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        context.set(REQUEST_CONTEXT_KEY, interceptorContext.allArguments()[1]);
    }

    /**
     * Get the {@link okhttp3.Request} from {@link EnhancedClassInstanceContext}, then create {@link Span} and set host,
     * port, kind, component, url from {@link okhttp3.Request}.
     * Through the reflection of the way, set the http header of context data into {@link okhttp3.Request#headers}.
     *
     * @param context instance context, a class instance only has one {@link EnhancedClassInstanceContext} instance.
     * @param interceptorContext method context, includes class name, method name, etc.
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        MethodInterceptResult result) throws Throwable {
        Request request = (Request)context.get(REQUEST_CONTEXT_KEY);

        AbstractSpan span = ContextManager.createSpan(request.url().uri().toString());
        span.setPeerHost(request.url().host());
        span.setPort(request.url().port());
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
        Tags.COMPONENT.set(span, COMPONENT_NAME);
        Tags.HTTP.METHOD.set(span, request.method());
        Tags.URL.set(span, request.url().url().getPath());
        Tags.SPAN_LAYER.asHttp(span);

        ContextCarrier contextCarrier = new ContextCarrier();
        ContextManager.inject(contextCarrier);

        Field headersField = Request.class.getDeclaredField("headers");
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(headersField, headersField.getModifiers() & ~Modifier.FINAL);

        headersField.setAccessible(true);
        Headers headers = request.headers().newBuilder().add(Config.Plugin.Propagation.HEADER_NAME, contextCarrier.serialize()).build();
        headersField.set(request, headers);
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
        int statusCode = response.code();

        AbstractSpan span = ContextManager.activeSpan();
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
