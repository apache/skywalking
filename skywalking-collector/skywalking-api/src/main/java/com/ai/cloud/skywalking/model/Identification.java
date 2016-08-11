package com.ai.cloud.skywalking.model;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.protocol.util.StringUtil;

import java.util.HashMap;
import java.util.Map;

public class Identification {
    private String              viewPoint;
    private Map<String, String> parameters;
    private String              businessKey;
    private String              spanTypeDesc;
    private String              callType;

    public Identification() {
        //Non
        parameters = new HashMap<String, String>();
    }

    public String getViewPoint() {
        return viewPoint;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String getSpanTypeDesc() {
        return spanTypeDesc;
    }

    public String getCallType() {
        return callType;
    }

    public String getBusinessKey() {
        return businessKey;
    }


    public static IdentificationBuilder newBuilder() {
        return new IdentificationBuilder();
    }

    public static class IdentificationBuilder {
        private Identification sendData;
        private int parameterIdx = 0;

        IdentificationBuilder() {
            sendData = new Identification();
        }

        public Identification build() {
            return sendData;
        }

        public IdentificationBuilder viewPoint(String viewPoint) {
            sendData.viewPoint = viewPoint;
            return this;
        }

        public IdentificationBuilder addParameter(String value){
            parameterIdx++;
            sendData.parameters.put("_" + parameterIdx, value);
            return this;
        }


        public IdentificationBuilder businessKey(String businessKey) {
            sendData.businessKey = businessKey;
            return this;
        }

        public IdentificationBuilder spanType(IBuriedPointType spanType) {
            if (StringUtil.isEmpty(spanType.getTypeName())) {
                throw new IllegalArgumentException("Span Type name cannot be null");
            }
            sendData.spanTypeDesc = spanType.getTypeName();
            sendData.callType = spanType.getCallType().toString();
            return this;
        }

    }


}
