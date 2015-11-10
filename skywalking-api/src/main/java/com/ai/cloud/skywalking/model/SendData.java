package com.ai.cloud.skywalking.model;

public class SendData {
    private String viewPoint;
    private String URI;
    private String businessKey;

    public SendData() {
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

    public static SendDataBuilder newBuilder() {
        return new SendDataBuilder();
    }

    public static class SendDataBuilder {
        private SendData sendData;

        SendDataBuilder() {
            sendData = new SendData();
        }

        public SendData build() {
            return sendData;
        }

        public SendDataBuilder viewPoint(String viewPoint) {
            sendData.viewPoint = viewPoint;
            return this;
        }

        public SendDataBuilder URI(String uri) {
            sendData.URI = uri;
            return this;
        }

        public SendDataBuilder businessKey(String businessKey) {
            sendData.businessKey = businessKey;
            return this;
        }

    }


}
