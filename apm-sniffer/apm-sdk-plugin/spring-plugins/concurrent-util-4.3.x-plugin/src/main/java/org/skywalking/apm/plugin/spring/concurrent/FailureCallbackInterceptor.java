package org.skywalking.apm.plugin.spring.concurrent;

import java.lang.reflect.Method;
import java.net.URI;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.ContextSnapshot;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

public class FailureCallbackInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        Object[] cacheValues = (Object[])objInst.getSkyWalkingDynamicField();
        if (cacheValues == null) {
            return;
        }

        URI uri = (URI)cacheValues[0];
        AbstractSpan span = ContextManager.createLocalSpan("future/failureCallback:" + uri.getPath());
        span.errorOccurred().log((Throwable)allArguments[0]).setComponent(ComponentsDefine.REST_TEMPLATE).setLayer(SpanLayer.HTTP);
        Tags.URL.set(span, uri.getPath());
        ContextManager.continued((ContextSnapshot)cacheValues[2]);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
