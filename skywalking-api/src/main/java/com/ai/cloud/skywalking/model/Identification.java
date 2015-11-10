package com.ai.cloud.skywalking.model;

public class Identification {
    private String viewPoint;
    private String businessKey;

    public Identification() {
        //Non
    }

    public String getViewPoint() {
        return viewPoint;
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

    }


}
