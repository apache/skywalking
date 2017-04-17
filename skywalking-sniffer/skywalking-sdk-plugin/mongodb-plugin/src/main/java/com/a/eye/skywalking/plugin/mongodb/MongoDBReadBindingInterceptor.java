package com.a.eye.skywalking.plugin.mongodb;

import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.mongodb.ServerAddress;
import com.mongodb.binding.ReadBinding;

/**
 * {@link MongoDBReadBindingInterceptor} record the host and port information from {@link EnhancedClassInstanceContext#context},
 *
 * @author baiyang
 */
public class MongoDBReadBindingInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            MethodInterceptResult result) {
        
    }

    /**
     * Execute after {@link com.mongodb.Mongo#getReadBinding(ReadPreference)},
     * record the host and port information
     */
    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            Object ret) {
        ReadBinding readBinding = (ReadBinding) ret;
        ServerAddress serverAddress = readBinding.getReadConnectionSource().getServerDescription().getAddress();
        String host = serverAddress.getHost();
        Integer port = serverAddress.getPort();
        context.set(MongoDBMethodInterceptor.MONGODB_HOST, host);
        context.set(MongoDBMethodInterceptor.MONGODB_PORT, port);
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
            InstanceMethodInvokeContext interceptorContext) {
        ContextManager.activeSpan().log(t);
    }

}
