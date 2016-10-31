package com.a.eye.skywalking.plugin.jedis.v2;

import com.a.eye.skywalking.invoke.monitor.RPCClientInvokeMonitor;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.assist.SimpleObjectFirstInvokeInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;

public abstract class JedisBaseInterceptor extends SimpleObjectFirstInvokeInterceptor {
    protected static final String REDIS_CONN_INFO_KEY = "redisClusterConnInfo";

    private static RPCClientInvokeMonitor rpcClientInvokeMonitor = new RPCClientInvokeMonitor();

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        if (this.isFirstBeforeMethod(context)) {
            /**
             * redis server wouldn't process rpc context. ignore the
             * return(ContextData) of sender's beforeSend
             */
            Identification.IdentificationBuilder builder = Identification
                    .newBuilder()
                    .viewPoint(
                            context.get(REDIS_CONN_INFO_KEY, String.class)
                                    + " " + interceptorContext.methodName())
                    .spanType(RedisBuriedPointType.instance());
            if (interceptorContext.allArguments().length > 0
                    && interceptorContext.allArguments()[0] instanceof String) {
                builder.businessKey("key="
                        + interceptorContext.allArguments()[0]);
            }
            rpcClientInvokeMonitor.beforeInvoke(builder.build());
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        if (this.isLastAfterMethod(context)) {
            rpcClientInvokeMonitor.afterInvoke();
        }
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
        rpcClientInvokeMonitor.occurException(t);
    }
}
