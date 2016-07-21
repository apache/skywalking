package com.ai.cloud.skywalking.transformer;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.invoke.monitor.LocalMethodInvokeMonitor;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.common.CallType;
import com.google.gson.Gson;

public class MethodInterceptor {


    public void before(Class originClass, Class[] parametersType, Object[] allArgument, Object superCall,
            String methodName) {
        LocalMethodInvokeMonitor localMethodInvokeMonitor = new LocalMethodInvokeMonitor();
        Identification.IdentificationBuilder identificationBuilder = Identification.newBuilder();
        identificationBuilder.viewPoint(generateViewPoint(originClass, parametersType, methodName));
        appendingParameters(allArgument, identificationBuilder);
        identificationBuilder.spanType(new IBuriedPointType() {
            @Override
            public String getTypeName() {
                return "LOCAL";
            }

            @Override
            public CallType getCallType() {
                return CallType.SYNC;
            }
        });

        localMethodInvokeMonitor.beforeInvoke(identificationBuilder.build());

    }

    private void appendingParameters(Object[] allArgument, Identification.IdentificationBuilder identificationBuilder) {
        for (int i = 0; i < allArgument.length; i++) {
            try {
                identificationBuilder.appendParameter("P" + i, new Gson().toJson(allArgument[i]));
            } catch (Exception e) {
                identificationBuilder.appendParameter("P" + i, "Cannot convert parameter");
            }
        }
    }

    private String generateViewPoint(Class originClass, Class[] parametersType, String methodName) {
        StringBuilder viewPoint = new StringBuilder(originClass.getName() + "." + methodName + "(");
        for (Class parameterType : parametersType) {
            viewPoint.append(parameterType.getClass() + ",");
        }
        viewPoint.append(")");
        return viewPoint.toString();
    }


    public void handleException(Throwable e) {
        new LocalMethodInvokeMonitor().occurException(e);
    }


    public void after(Class originClass, Class resultType, Object result) {
        String resultJson = null;
        if (!void.class.getName().equals(resultType.getName())) {
            try {
                resultJson = new Gson().toJson(result);
            } catch (Exception e) {
                resultJson = "Can not convert result";
            }
        }

        new LocalMethodInvokeMonitor().afterInvoke(resultJson);
    }


}
