package com.a.eye.skywalking.plugin.motan;

import com.a.eye.skywalking.invoke.monitor.RPCServerInvokeMonitor;
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

public class MotanServerInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {
    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        context.set("serviceURI", interceptorContext.allArguments()[0]);
    }

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                             MethodInterceptResult result) {
        com.weibo.api.motan.rpc.Request request = (com.weibo.api.motan.rpc.Request) interceptorContext.allArguments()[0];
        if (request != null) {
            new RPCServerInvokeMonitor().beforeInvoke(new ContextData(request.getAttachments().get("contextData")),
                    generateIdentify(request, (com.weibo.api.motan.rpc.URL) context.get("serviceURI")));
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                              Object ret) {
        new RPCServerInvokeMonitor().afterInvoke();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
                                      InstanceMethodInvokeContext interceptorContext) {
        new RPCServerInvokeMonitor().occurException(t);
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
