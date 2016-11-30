package com.a.eye.skywalking.plugin.tomcat78x;

import com.a.eye.skywalking.api.Tracing;
import com.a.eye.skywalking.invoke.monitor.RPCServerInvokeMonitor;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TomcatPluginInterceptor implements InstanceMethodsAroundInterceptor {
    private final String secondKey = "ContextData";
    private String tracingName = DEFAULT_TRACE_NAME;
    private static final String DEFAULT_TRACE_NAME = "SkyWalking-TRACING-NAME";
    private static final String TRACE_ID_HEADER_NAME = "SW-TraceId";

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        Object[] args = interceptorContext.allArguments();
        HttpServletRequest requests = (HttpServletRequest) args[0];
        String tracingHeaderValue = requests.getHeader(tracingName);
        ContextData contextData = null;
        if (tracingHeaderValue != null) {
            String contextDataStr = null;
            int index = tracingHeaderValue.indexOf("=");
            if (index > 0) {
                String key = tracingHeaderValue.substring(0, index);
                if (secondKey.equals(key)) {
                    contextDataStr = tracingHeaderValue.substring(index + 1);
                }
            }

            if (contextDataStr != null && contextDataStr.length() > 0) {
                contextData = new ContextData(contextDataStr);
            }
        }
        RPCServerInvokeMonitor rpcServerInvokeMonitor = new RPCServerInvokeMonitor();
        rpcServerInvokeMonitor.beforeInvoke(contextData, generateIdentification(requests));
    }


    private Identification generateIdentification(HttpServletRequest request) {
        return Identification.newBuilder()
                .viewPoint(request.getRequestURL().toString())
                .spanType(WebBuriedPointType.instance())
                .build();
    }


    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        Object[] args = interceptorContext.allArguments();
        HttpServletResponse httpServletResponse = (HttpServletResponse) args[1];
        httpServletResponse.addHeader(TRACE_ID_HEADER_NAME, Tracing.getTraceId());
        new RPCServerInvokeMonitor().afterInvoke();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
            InstanceMethodInvokeContext interceptorContext) {
        new RPCServerInvokeMonitor().occurException(t);
    }

}
