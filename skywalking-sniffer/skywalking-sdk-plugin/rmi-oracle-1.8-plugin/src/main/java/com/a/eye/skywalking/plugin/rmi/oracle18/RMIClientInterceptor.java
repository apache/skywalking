package com.a.eye.skywalking.plugin.rmi.oracle18;

import com.a.eye.skywalking.invoke.monitor.RPCClientInvokeMonitor;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Created by xin on 2016/12/22.
 */
public class RMIClientInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        Object[] arguments = (Object[]) interceptorContext.allArguments()[3];
        ContextData contextData = new RPCClientInvokeMonitor()
                .beforeInvoke(Identification.newBuilder().viewPoint(((Method) interceptorContext.
                        allArguments()[2]).getName()).spanType(RMIBuriedPointType.INSTANCE).build());
        String contextDataStr = contextData.toString();

        Object[] newArguments = Arrays.copyOf(arguments, arguments.length + 1);
        newArguments[arguments.length] = contextDataStr;

        interceptorContext.allArguments()[3] = newArguments;
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        new RPCClientInvokeMonitor().afterInvoke();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
        new RPCClientInvokeMonitor().occurException(t);
    }
}
