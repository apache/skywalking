package com.ai.cloud.skywalking.jedis.v2.plugin;

import com.ai.cloud.skywalking.tracer.RPCClientTracer;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.assist.SimpleObjectFirstInvokeInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.MethodInterceptResult;

public abstract class JedisBaseInterceptor extends SimpleObjectFirstInvokeInterceptor {
    protected static final String REDIS_CONN_INFO_KEY = "redisClusterConnInfo";

    private static RPCClientTracer clientTracer = new RPCClientTracer();

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
            clientTracer.traceBeforeInvoke(builder.build());
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        if (this.isLastAfterMethod(context)) {
            clientTracer.traceAfterInvoke();
        }
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        clientTracer.occurException(t);
    }
}
