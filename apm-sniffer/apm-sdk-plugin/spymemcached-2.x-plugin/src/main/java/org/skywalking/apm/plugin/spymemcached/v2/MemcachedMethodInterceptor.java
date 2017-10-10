package org.skywalking.apm.plugin.spymemcached.v2;

import java.lang.reflect.Method;

import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

public class MemcachedMethodInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String SPY_MEMCACHE = "SpyMemcached/";

    @Override 
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        String peer = String.valueOf(objInst.getSkyWalkingDynamicField());
        AbstractSpan span = ContextManager.createExitSpan(SPY_MEMCACHE + method.getName(), peer);
        span.setComponent(ComponentsDefine.MEMCACHE);
        Tags.DB_TYPE.set(span, ComponentsDefine.MEMCACHE.getName());
        SpanLayer.asDB(span);
        Tags.DB_STATEMENT.set(span, method.getName() + " " + allArguments[0]);
    }

    @Override 
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ContextManager.activeSpan();
        span.errorOccurred();
        span.log(t);
    }
}
