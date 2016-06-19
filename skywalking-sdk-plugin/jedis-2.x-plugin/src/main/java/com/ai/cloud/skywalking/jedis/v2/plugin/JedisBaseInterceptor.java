package com.ai.cloud.skywalking.jedis.v2.plugin;

import com.ai.cloud.skywalking.buriedpoint.RPCBuriedPointSender;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.assist.SimpleObjectFirstInvokeInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;

public abstract class JedisBaseInterceptor extends SimpleObjectFirstInvokeInterceptor {
    protected static final String REDIS_CONN_INFO_KEY = "redisClusterConnInfo";

    private static RPCBuriedPointSender sender = new RPCBuriedPointSender();

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
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
            sender.beforeSend(builder.build());
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        if (this.isLastAfterMethod(context)) {
            sender.afterSend();
        }
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        sender.handleException(t);
    }
}
