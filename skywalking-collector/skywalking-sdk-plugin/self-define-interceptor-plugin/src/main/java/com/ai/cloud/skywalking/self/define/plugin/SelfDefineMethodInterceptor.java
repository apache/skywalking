package com.ai.cloud.skywalking.self.define.plugin;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.invoke.monitor.LocalMethodInvokeMonitor;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.*;
import com.google.gson.Gson;

public class SelfDefineMethodInterceptor implements InstanceMethodsAroundInterceptor, StaticMethodsAroundInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
    }

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            MethodInterceptResult result) {
        Identification.IdentificationBuilder identificationBuilder = buildIdentificationBuilder(interceptorContext);
        new LocalMethodInvokeMonitor().beforeInvoke(identificationBuilder.build());
    }

    private Identification.IdentificationBuilder buildIdentificationBuilder(MethodInvokeContext interceptorContext) {
        Identification.IdentificationBuilder identificationBuilder = Identification.newBuilder();
        if (Config.SkyWalking.RECORD_PARAM) {
            for (Object param : interceptorContext.allArguments()) {
                String paramStr;
                try {
                    paramStr = new Gson().toJson(param);
                } catch (Exception e) {
                    paramStr = "N/A";
                }
                identificationBuilder.addParameter(paramStr);
            }
        }

        identificationBuilder.spanType(new SelfDefineSpanType()).viewPoint(interceptorContext.methodName());
        return identificationBuilder;
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            Object ret) {
        new LocalMethodInvokeMonitor().afterInvoke();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
            InstanceMethodInvokeContext interceptorContext) {
        new LocalMethodInvokeMonitor().occurException(t);
    }

    @Override
    public void beforeMethod(MethodInvokeContext interceptorContext, MethodInterceptResult result) {
        new LocalMethodInvokeMonitor().beforeInvoke(buildIdentificationBuilder(interceptorContext).build());
    }

    @Override
    public Object afterMethod(MethodInvokeContext interceptorContext, Object ret) {
        new LocalMethodInvokeMonitor().afterInvoke();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, MethodInvokeContext interceptorContext) {
        new LocalMethodInvokeMonitor().occurException(t);
    }
}
