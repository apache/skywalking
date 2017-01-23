package com.a.eye.skywalking.model;

import com.a.eye.skywalking.api.IBuriedPointType;
import com.a.eye.skywalking.util.StringUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Identification {
    private String              viewPoint;
    private String              businessKey;
    private String              spanTypeDesc;
    private String              callType;
    private long                startTimestamp;
    private Map<String, String> tags = new HashMap<String, String>();

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

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public Map<String, String> getTags() {
        if (tags == null){
            return Collections.EMPTY_MAP;
        }
        return tags;
    }

    public static IdentificationBuilder newBuilder() {
        return new IdentificationBuilder();
    }

    public static class IdentificationBuilder {
        private Identification identification;

        IdentificationBuilder() {
            identification = new Identification();
        }

        public Identification build() {
            return identification;
        }

        public IdentificationBuilder viewPoint(String viewPoint) {
            identification.viewPoint = viewPoint;
            return this;
        }

        public IdentificationBuilder businessKey(String businessKey) {
            identification.businessKey = businessKey;
            return this;
        }

        public IdentificationBuilder spanType(IBuriedPointType spanType) {
            if (StringUtil.isEmpty(spanType.getTypeName())) {
                throw new IllegalArgumentException("Span Type name cannot be null");
            }
            identification.spanTypeDesc = spanType.getTypeName();
            identification.callType = spanType.getCallType().toString();
            return this;
        }

        public IdentificationBuilder startTime(long startTime) {
            identification.startTimestamp = startTime;
            return this;
        }

        public IdentificationBuilder tag(String tagKey, String tagValue) {
            identification.tags.put(tagKey, tagValue);
            return this;
        }
    }


}
