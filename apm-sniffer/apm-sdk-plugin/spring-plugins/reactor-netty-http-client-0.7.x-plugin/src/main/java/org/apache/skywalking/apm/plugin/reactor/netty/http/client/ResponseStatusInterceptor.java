package org.apache.skywalking.apm.plugin.reactor.netty.http.client;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.*;

/**
 * @author jian.tan
 */
public class ResponseStatusInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        HttpResponseStatus status = (HttpResponseStatus)ret;
        if (status.code() > 400) {
            ContextManager.activeSpan().errorOccurred();
            Tags.STATUS_CODE.set(ContextManager.activeSpan(), String.valueOf(status.code()));
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
