package com.a.eye.skywalking.plugin.motan;

import com.a.eye.skywalking.invoke.monitor.RPCClientInvokeMonitor;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;
import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.URL;

/**
 * Motan client interceptor
 */
public class MotanClientCallInterceptor implements InstanceMethodsAroundInterceptor{

    private static final String REQUEST_URL = "REQUEST_URL";

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                             MethodInterceptResult result) {
        URL url = (URL) context.get(REQUEST_URL);
        Request request = (Request) interceptorContext.allArguments()[0];
        if (url != null) {
            ContextData contextData = new RPCClientInvokeMonitor()
                    .beforeInvoke(generateIdentify(request, url));
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


    private static String generateViewPoint(URL serviceURI, Request request) {
        StringBuilder viewPoint = new StringBuilder(serviceURI.getUri());
        viewPoint.append("." + request.getMethodName());
        viewPoint.append("(" + request.getParamtersDesc() + ")?group=" + serviceURI.getGroup());
        return viewPoint.toString();
    }


    public static Identification generateIdentify(Request request, URL serviceURI) {
        return Identification.newBuilder().viewPoint(generateViewPoint(serviceURI, request))
                .spanType(MotanBuriedPointType.INSTANCE).build();
    }
}
