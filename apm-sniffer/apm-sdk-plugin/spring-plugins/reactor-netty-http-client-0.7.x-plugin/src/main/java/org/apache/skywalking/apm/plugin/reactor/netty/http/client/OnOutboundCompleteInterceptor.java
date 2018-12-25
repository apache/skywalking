package org.apache.skywalking.apm.plugin.reactor.netty.http.client;

import io.netty.handler.codec.http.HttpRequest;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.*;

/**
 * @author jian.tan
 */
public class OnOutboundCompleteInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        HttpRequest request = (HttpRequest)ContextManager.getRuntimeContext().get("SW_NETTY_HTTP_CLIENT_REQUEST");
        if (request != null) {
            ContextManager.stopSpan();
            ContextManager.getRuntimeContext().remove("SW_NETTY_HTTP_CLIENT_REQUEST");
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
