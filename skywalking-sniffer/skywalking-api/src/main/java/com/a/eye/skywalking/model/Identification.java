package com.a.eye.skywalking.model;

import com.a.eye.skywalking.api.IBuriedPointType;
import com.a.eye.skywalking.util.StringUtil;

public class Identification {
    private String              viewPoint;
    private String              businessKey;
    private String              spanTypeDesc;
    private String              callType;

    public Identification() {
        //Non
    }

    public String getViewPoint() {
        return viewPoint;
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
