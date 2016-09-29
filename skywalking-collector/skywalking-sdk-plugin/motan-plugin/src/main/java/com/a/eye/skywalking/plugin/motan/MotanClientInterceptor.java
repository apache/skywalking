package com.a.eye.skywalking.plugin.motan;

import com.a.eye.skywalking.invoke.monitor.RPCClientInvokeMonitor;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;
import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.URL;

import static com.a.eye.skywalking.plugin.motan.IdentificationUtil.generateIdentify;

/**
 * Motan client interceptor
 */
public class MotanClientInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        context.set("serviceURI", interceptorContext.allArguments()[1]);
    }

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            MethodInterceptResult result) {
        Request request = (Request) interceptorContext.allArguments()[0];
        if (request != null) {
            ContextData contextData = new RPCClientInvokeMonitor()
                    .beforeInvoke(generateIdentify(request, (URL) context.get("serviceURI")));
            String contextDataStr = contextData.toString();
            request.setAttachment("contextData", contextDataStr);
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            Object ret) {
        new RPCClientInvokeMonitor().afterInvoke();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
            InstanceMethodInvokeContext interceptorContext) {
        new RPCClientInvokeMonitor().occurException(t);
    }

}
