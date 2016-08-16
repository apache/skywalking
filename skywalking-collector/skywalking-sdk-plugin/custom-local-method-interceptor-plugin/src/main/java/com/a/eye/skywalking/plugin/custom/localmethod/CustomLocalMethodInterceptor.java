package com.a.eye.skywalking.plugin.custom.localmethod;

import com.a.eye.skywalking.api.Tracing;
import com.a.eye.skywalking.buffer.ContextBuffer;
import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.invoke.monitor.LocalMethodInvokeMonitor;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.*;
import com.a.eye.skywalking.protocol.InputParametersSpan;
import com.a.eye.skywalking.protocol.OutputParameterSpan;
import com.google.gson.Gson;

public class CustomLocalMethodInterceptor implements InstanceMethodsAroundInterceptor, StaticMethodsAroundInterceptor {

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
    }

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            MethodInterceptResult result) {
        Identification.IdentificationBuilder identificationBuilder = Identification.newBuilder();
        identificationBuilder.spanType(new CustomLocalSpanType()).viewPoint(
                fullMethodName(interceptorContext.inst().getClass(), interceptorContext.methodName(),
                        interceptorContext.argumentTypes()));

        new LocalMethodInvokeMonitor().beforeInvoke(identificationBuilder.build());

        recordParametersAndSave2BufferIfNecessary(interceptorContext.allArguments());
    }

    private void recordParametersAndSave2BufferIfNecessary(Object[] arguments) {
        if (Config.Plugin.CustomLocalMethodInterceptorPlugin.RECORD_PARAM_ENABLE) {
            InputParametersSpan inputParametersSpan = new InputParametersSpan(Tracing.getTraceId(), Tracing.getTracelevelId());
            for (Object param : arguments) {
                String paramStr;
                try {
                    paramStr = new Gson().toJson(param);
                } catch (Throwable e) {
                    paramStr = "N/A";
                }
                inputParametersSpan.addParameter(paramStr);
            }

            ContextBuffer.save(inputParametersSpan);
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            Object ret) {
        recordResultAndSave2BufferIfNecessary(ret);
        new LocalMethodInvokeMonitor().afterInvoke();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
            InstanceMethodInvokeContext interceptorContext) {
        new LocalMethodInvokeMonitor().occurException(t);
    }


    @Override
    public void beforeMethod(StaticMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        Identification.IdentificationBuilder identificationBuilder = Identification.newBuilder();
        identificationBuilder.spanType(new CustomLocalSpanType()).viewPoint(
                fullMethodName(interceptorContext.claszz(), interceptorContext.methodName(),
                        interceptorContext.argumentTypes()));

        new LocalMethodInvokeMonitor().beforeInvoke(identificationBuilder.build());

        recordParametersAndSave2BufferIfNecessary(interceptorContext.allArguments());
    }

    @Override
    public Object afterMethod(StaticMethodInvokeContext interceptorContext, Object ret) {
        recordResultAndSave2BufferIfNecessary(ret);
        new LocalMethodInvokeMonitor().afterInvoke();
        return ret;
    }

    private void recordResultAndSave2BufferIfNecessary(Object ret) {
        if (Config.Plugin.CustomLocalMethodInterceptorPlugin.RECORD_PARAM_ENABLE){
            OutputParameterSpan outputParameterSpan = new OutputParameterSpan(Tracing.getTraceId(), Tracing.getTracelevelId());
            String retStr;
            try{
                retStr = new Gson().toJson(ret);
            }catch (Throwable e){
                retStr = "N/A";
            }
            outputParameterSpan.setOutputParameter(retStr);
            ContextBuffer.save(outputParameterSpan);
        }
    }

    @Override
    public void handleMethodException(Throwable t, MethodInvokeContext interceptorContext) {
        new LocalMethodInvokeMonitor().occurException(t);
    }

    private String fullMethodName(Class clazz, String simpleMethodName, Class[] allArgumentTypes) {
        StringBuilder methodName = new StringBuilder(clazz.getName() + "." + simpleMethodName + "(");
        for (Class argument : allArgumentTypes) {
            methodName.append(argument.getName() + ",");
        }

        if (allArgumentTypes.length > 0) {
            methodName.deleteCharAt(methodName.length() - 1);
        }

        methodName.append(")");
        return methodName.toString();
    }
}
