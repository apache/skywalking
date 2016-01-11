package com.ai.cloud.skywalking.model;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.util.StringUtil;
import com.sun.xml.internal.txw2.IllegalSignatureException;

public class Identification {
    private String viewPoint;
    private String businessKey;
    private String spanType;
    private String callType;

    public Identification() {
        //Non
    }

    public String getViewPoint() {
        return viewPoint;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getSpanType() {
        return spanType;
    }

    public String getCallType() {
        return callType;
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
                throw new IllegalSignatureException("Span Type name cannot be null");
            }
            sendData.spanType = spanType.getTypeName();
            sendData.callType = spanType.getCallType().toString();
            return this;
        }

    }


}
