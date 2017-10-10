package org.skywalking.apm.plugin.sjdbc.define;

import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.executor.ExecuteCallback;
import com.dangdang.ddframe.rdb.sharding.executor.threadlocal.ExecutorDataMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.ContextSnapshot;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

/**
 * {@link AsyncExecuteInterceptor} enhances {@link com.dangdang.ddframe.rdb.sharding.executor.ExecutorEngine#asyncExecute(SQLType, Collection, List, ExecuteCallback)} 
 * so that the sql executor can get a {@link ContextSnapshot} of main thread when it is executed asynchronously.
 * 
 * @author gaohongtao
 */
public class AsyncExecuteInterceptor implements InstanceMethodsAroundInterceptor {
    
    public static final String SNAPSHOT_DATA_KEY = "APM_SKYWALKING_SNAPSHOT_DATA";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        ExecutorDataMap.getDataMap().put(SNAPSHOT_DATA_KEY, ContextManager.capture());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        Map<String, Object> oldMap = ExecutorDataMap.getDataMap();
        Map<String, Object> newMap = new HashMap<>(oldMap.size() - 1);
        for (Map.Entry<String, Object> each : oldMap.entrySet()) {
            if (!each.getKey().equals(SNAPSHOT_DATA_KEY)) {
                newMap.put(each.getKey(), each.getValue());
            }
        }
        ExecutorDataMap.setDataMap(newMap);
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
    }
}
