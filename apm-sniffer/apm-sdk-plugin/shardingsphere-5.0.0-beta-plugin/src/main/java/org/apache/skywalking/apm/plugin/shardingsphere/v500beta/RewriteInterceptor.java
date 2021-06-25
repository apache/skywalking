package org.apache.skywalking.apm.plugin.shardingsphere.v500beta;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

/**
 * {@link RewriteInterceptor} enhances {@link org.apache.shardingsphere.infra.rewrite.SQLRewriteEntry},
 * creating a local span that records the route of sql.
 */
public class RewriteInterceptor implements InstanceMethodsAroundInterceptor {
    
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) {
        ContextManager.createLocalSpan("/ShardingSphere/rewriteSQL/")
                .setComponent(ComponentsDefine.SHARDING_SPHERE);
    }
    
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) {
        ContextManager.stopSpan();
        return ret;
    }
    
    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}
