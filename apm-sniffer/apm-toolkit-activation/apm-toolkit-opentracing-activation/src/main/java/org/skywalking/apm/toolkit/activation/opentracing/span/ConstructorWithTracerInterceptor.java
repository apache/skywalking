package org.skywalking.apm.toolkit.activation.opentracing.span;

import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class ConstructorWithTracerInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        objInst.setSkyWalkingDynamicField(ContextManager.activeSpan());
    }
}
