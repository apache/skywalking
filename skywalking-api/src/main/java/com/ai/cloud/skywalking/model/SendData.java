package com.ai.cloud.skywalking.model;

public class SendData {
    private String viewPoint;
    private String URI;
    private String businessKey;

    private SendData() {
        //Non
    }

    public String getViewPoint() {
        return viewPoint;
    }

    public String getURI() {
        return URI;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public static BaseSendDataBuilder newBuilder() {
        return new BaseSendDataBuilder();
    }

    public static class BaseSendDataBuilder {
        private SendData sendData;

        BaseSendDataBuilder() {
            sendData = new SendData();
        }

        public SendData build() {
            return sendData;
        }

        public BaseSendDataBuilder viewPoint(String viewPoint) {
            sendData.viewPoint = viewPoint;
            return this;
        }

        public BaseSendDataBuilder URI(String uri) {
            sendData.URI = uri;
            return this;
        }

        public BaseSendDataBuilder businessKey(String businessKey) {
            sendData.businessKey = businessKey;
            return this;
        }

    }


}
