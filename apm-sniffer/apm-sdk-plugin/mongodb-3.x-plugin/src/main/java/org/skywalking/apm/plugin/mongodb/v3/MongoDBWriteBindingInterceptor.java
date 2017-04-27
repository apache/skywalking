package org.skywalking.apm.plugin.mongodb.v3;

import com.mongodb.ServerAddress;
import com.mongodb.binding.WriteBinding;
import org.skywalking.apm.api.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.api.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * {@link MongoDBWriteBindingInterceptor} record the host and port information from {@link
 * EnhancedClassInstanceContext#context}
 *
 * @author baiyang
 */
public class MongoDBWriteBindingInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                             MethodInterceptResult result) {
    }

    /**
     * Execute after {@link com.mongodb.Mongo#getWriteBinding()},
     * record the host and port information
     */
    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                              Object ret) {
        WriteBinding writeBinding = (WriteBinding) ret;
        ServerAddress serverAddress = writeBinding.getWriteConnectionSource().getServerDescription().getAddress();
        String host = serverAddress.getHost();
        Integer port = serverAddress.getPort();
        context.set(MongoDBMethodInterceptor.MONGODB_HOST, host);
        context.set(MongoDBMethodInterceptor.MONGODB_PORT, port);
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
                                      InstanceMethodInvokeContext interceptorContext) {
    }

}
