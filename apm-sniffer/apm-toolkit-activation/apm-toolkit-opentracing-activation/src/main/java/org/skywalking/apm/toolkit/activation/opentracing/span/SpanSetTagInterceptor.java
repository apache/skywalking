package org.skywalking.apm.toolkit.activation.opentracing.span;

import io.opentracing.tag.Tags;
import java.lang.reflect.Method;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

public class SpanSetTagInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        String tagKey = String.valueOf(allArguments[0]);
        String tagValue = String.valueOf(allArguments[1]);
        if (Tags.COMPONENT.getKey().equals(tagKey)) {
            activeSpan.setComponent(tagValue);
        } else if (Tags.PEER_SERVICE.getKey().equals(tagKey)) {
            activeSpan.setOperationName(tagValue);
        } else {
            activeSpan.tag(tagKey, tagValue);
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
