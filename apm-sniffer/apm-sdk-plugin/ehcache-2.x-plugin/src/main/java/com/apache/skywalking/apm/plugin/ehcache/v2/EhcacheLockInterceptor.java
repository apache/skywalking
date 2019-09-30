package com.apache.skywalking.apm.plugin.ehcache.v2;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

import static com.apache.skywalking.apm.plugin.ehcache.v2.define.EhcachePluginInstrumentation.LOCK_ENHANCE_METHOD_SUFFIX;

/**
 * @Author MrPro
 */
public class EhcacheLockInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        EhcacheEnhanceInfo enhanceInfo = (EhcacheEnhanceInfo) objInst.getSkyWalkingDynamicField();

        String operateName = method.getName().substring(0, method.getName().length() - LOCK_ENHANCE_METHOD_SUFFIX.length());

        AbstractSpan span = ContextManager.createLocalSpan("Ehcache/" + operateName + "/" + enhanceInfo.getCacheName());
        span.setComponent(ComponentsDefine.EHCACHE);
        SpanLayer.asCache(span);

        Object element = allArguments[0];
        if (element != null) {
            Tags.DB_STATEMENT.set(span, element.toString());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
