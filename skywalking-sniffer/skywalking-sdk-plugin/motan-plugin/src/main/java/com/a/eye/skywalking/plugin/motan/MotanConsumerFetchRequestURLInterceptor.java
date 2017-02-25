package com.a.eye.skywalking.plugin.motan;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * {@link MotanConsumerFetchRequestURLInterceptor} record {@link com.weibo.api.motan.rpc.URL} to {@link EnhancedClassInstanceContext#context}
 * for the operation name that create span need.
 *
 * @author zhangxin
 */
public class MotanConsumerFetchRequestURLInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String CONTEXT_NAME_OF_REQUEST_URL = "REQUEST_URL";

    /**
     * Fetch the request url from the first param of all constructor, and put request
     *  url into {@link EnhancedClassInstanceContext#context}.
     *
     * @param context            instance context, a class instance only has one {@link EnhancedClassInstanceContext} instance.
     * @param interceptorContext method context, includes class name, method name, etc.
     * @param result             change this result, if you want to truncate the method.
     */
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        context.set(CONTEXT_NAME_OF_REQUEST_URL, interceptorContext.allArguments()[0]);
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
        // do nothing
    }
}
