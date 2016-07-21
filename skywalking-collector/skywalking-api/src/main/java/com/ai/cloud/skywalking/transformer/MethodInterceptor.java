package com.ai.cloud.skywalking.transformer;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.invoke.monitor.LocalMethodInvokeMonitor;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.common.CallType;
import com.google.gson.Gson;

public class MethodInterceptor {
    public static IBuriedPointType METHOD_INVOKE_BURIEDPOINT = new IBuriedPointType() {
        @Override
        public String getTypeName() {
            return "LOCAL";
        }

        @Override
        public CallType getCallType() {
            return CallType.SYNC;
        }
    };

    public void before(Class originClass, Class[] parametersType, Object[] allArgument, Object superCall,
            String methodName) {
        LocalMethodInvokeMonitor localMethodInvokeMonitor = new LocalMethodInvokeMonitor();
        Identification.IdentificationBuilder identificationBuilder = Identification.newBuilder();
        identificationBuilder.viewPoint(generateViewPoint(originClass, parametersType, methodName));
        appendingParameters(allArgument, identificationBuilder);
        identificationBuilder.spanType(METHOD_INVOKE_BURIEDPOINT);

        localMethodInvokeMonitor.beforeInvoke(identificationBuilder.build());

    }

    private void appendingParameters(Object[] allArgument, Identification.IdentificationBuilder identificationBuilder) {
        Gson gson = new Gson();
        for (int i = 0; i < allArgument.length; i++) {
            try {
                identificationBuilder.addParameter(gson.toJson(allArgument[i]));
            } catch (Exception e) {
                identificationBuilder.addParameter("N/A");
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
                resultJson = "N/A";
            }
        }

        new LocalMethodInvokeMonitor().afterInvoke(resultJson);
    }


}
