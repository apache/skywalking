package org.skywalking.apm.plugin.okhttp.v3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.skywalking.apm.agent.core.context.CarrierItem;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link RealCallInterceptor} intercept the synchronous http calls by the discovery of okhttp.
 *
 * @author pengys5
 */
public class RealCallInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    /**
     * Intercept the {@link okhttp3.RealCall#RealCall(OkHttpClient, Request, boolean)}, then put the second argument of
     * {@link okhttp3.Request} into {@link EnhancedInstance}.
     *
     * @param objInst a new added instance field
     * @param allArguments constructor invocation context.
     */
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        objInst.setSkyWalkingDynamicField(allArguments[1]);
    }

    /**
     * Get the {@link okhttp3.Request} from {@link EnhancedInstance}, then create {@link AbstractSpan} and set host,
     * port, kind, component, url from {@link okhttp3.Request}.
     * Through the reflection of the way, set the http header of context data into {@link okhttp3.Request#headers}.
     *
     * @param method
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        Request request = (Request)objInst.getSkyWalkingDynamicField();

        ContextCarrier contextCarrier = new ContextCarrier();
        HttpUrl requestUrl = request.url();
        AbstractSpan span = ContextManager.createExitSpan(requestUrl.uri().getPath(), contextCarrier, requestUrl.host() + ":" + requestUrl.port());
        span.setComponent(ComponentsDefine.OKHTTP);
        Tags.HTTP.METHOD.set(span, request.method());
        Tags.URL.set(span, requestUrl.uri().toString());
        SpanLayer.asHttp(span);

        Field headersField = Request.class.getDeclaredField("headers");
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(headersField, headersField.getModifiers() & ~Modifier.FINAL);

        headersField.setAccessible(true);
        Headers.Builder headerBuilder = request.headers().newBuilder();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            headerBuilder.add(next.getHeadKey(), next.getHeadValue());
        }
        headersField.set(request, headerBuilder.build());
    }

    /**
     * Get the status code from {@link Response}, when status code greater than 400, it means there was some errors in
     * the server.
     * Finish the {@link AbstractSpan}.
     *
     * @param method
     * @param ret the method's original return value.
     * @return
     * @throws Throwable
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        Response response = (Response)ret;
        int statusCode = response.code();

        AbstractSpan span = ContextManager.activeSpan();
        if (statusCode >= 400) {
            span.errorOccurred();
            Tags.STATUS_CODE.set(span, Integer.toString(statusCode));
        }

        ContextManager.stopSpan();

        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan abstractSpan = ContextManager.activeSpan();
        abstractSpan.errorOccurred();
        abstractSpan.log(t);
    }
}
