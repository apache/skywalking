package org.skywalking.apm.plugin.sjdbc.define;

import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.executor.ExecuteCallback;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link ExecuteInterceptor} enhances {@link com.dangdang.ddframe.rdb.sharding.executor.ExecutorEngine#execute(SQLType, Collection, List, ExecuteCallback)}
 * ,creating a local span that records the overall execution of sql
 * 
 * @author gaohongtao
 */
public class ExecuteInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        SQLType sqlType = (SQLType)allArguments[0];
        ContextManager.createLocalSpan("/SJDBC/TRUNK/" + sqlType.name()).setComponent(ComponentsDefine.SHARDING_JDBC);
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
