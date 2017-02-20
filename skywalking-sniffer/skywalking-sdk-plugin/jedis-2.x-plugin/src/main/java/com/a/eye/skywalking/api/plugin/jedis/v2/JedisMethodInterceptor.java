package com.a.eye.skywalking.api.plugin.jedis.v2;

import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.assist.NoCocurrencyAceessObject;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;

public class JedisMethodInterceptor extends NoCocurrencyAceessObject {
    protected static final String REDIS_CONN_INFO_KEY = "redisClusterConnInfo";


    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        this.whenEnter(context, new Runnable() {
            @Override public void run() {
                /**
                 * redis server wouldn't process rpc context. ignore the
                 * return(ContextData) of sender's beforeSend
                 */
                Identification.IdentificationBuilder builder = Identification
                    .newBuilder()
                    .viewPoint(
                        context.get(REDIS_CONN_INFO_KEY, String.class)
                            + " " + interceptorContext.methodName())
                    .spanType(RedisBuriedPointType.INSTANCE);
                if (interceptorContext.allArguments().length > 0
                    && interceptorContext.allArguments()[0] instanceof String) {
                    builder.businessKey("key="
                        + interceptorContext.allArguments()[0]);
                }
                rpcClientInvokeMonitor.beforeInvoke(builder.build());
            }
        });
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        this.whenExist(context, new Runnable(){
            @Override public void run() {
                rpcClientInvokeMonitor.afterInvoke();
            }
        });

        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
        rpcClientInvokeMonitor.occurException(t);
    }
}
