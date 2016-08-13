package com.ai.cloud.skywalking.plugin.custom.localmethod;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.invoke.monitor.LocalMethodInvokeMonitor;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.*;
import com.google.gson.Gson;

public class CustomLocalMethodInterceptor implements InstanceMethodsAroundInterceptor, StaticMethodsAroundInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
    }

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            MethodInterceptResult result) {
        Identification.IdentificationBuilder identificationBuilder = buildIdentificationBuilder(interceptorContext);
        identificationBuilder.spanType(new CustomLocalSpanType()).viewPoint(
                fullMethodName(interceptorContext.inst().getClass(), interceptorContext.methodName(),
                        interceptorContext.argumentTypes()));
        new LocalMethodInvokeMonitor().beforeInvoke(identificationBuilder.build());
    }

    private Identification.IdentificationBuilder buildIdentificationBuilder(MethodInvokeContext interceptorContext) {
        Identification.IdentificationBuilder identificationBuilder = Identification.newBuilder();
        if (Config.Plugin.CustomLocalMethodInterceptorPlugin.RECORD_PARAM_ENABLE) {
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

        return identificationBuilder;
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            Object ret) {
        recordResultIfNecessary(ret);
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
            InstanceMethodInvokeContext interceptorContext) {
        new LocalMethodInvokeMonitor().occurException(t);
    }


    @Override
    public void beforeMethod(StaticMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        Identification.IdentificationBuilder identificationBuilder = buildIdentificationBuilder(interceptorContext);
        identificationBuilder.spanType(new CustomLocalSpanType()).viewPoint(
                fullMethodName(interceptorContext.claszz(), interceptorContext.methodName(),
                        interceptorContext.argumentTypes()));
        new LocalMethodInvokeMonitor().beforeInvoke(identificationBuilder.build());
    }

    @Override
    public Object afterMethod(StaticMethodInvokeContext interceptorContext, Object ret) {
        recordResultIfNecessary(ret);
        return ret;
    }

    private void recordResultIfNecessary(Object ret) {
        if (Config.Plugin.CustomLocalMethodInterceptorPlugin.RECORD_PARAM_ENABLE){
            String retStr;
            try{
                retStr = new Gson().toJson(ret);
            }catch (Exception e){
                retStr = "N/A";
            }
            new LocalMethodInvokeMonitor().afterInvoke(retStr);
        }else {
            new LocalMethodInvokeMonitor().afterInvoke();
        }
    }

    @Override
    public void handleMethodException(Throwable t, MethodInvokeContext interceptorContext) {
        new LocalMethodInvokeMonitor().occurException(t);
    }

    private String fullMethodName(Class clazz, String simpleMethodName, Object[] allArguments) {
        StringBuilder methodName = new StringBuilder(clazz.getName() + "." + simpleMethodName + "(");
        for (Object argument : allArguments) {
            methodName.append(argument.getClass() + ",");
        }

        if (allArguments.length > 0) {
            methodName.deleteCharAt(methodName.length() - 1);
        }

        methodName.append(")");
        return methodName.toString();
    }
}
