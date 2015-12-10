package com.ai.cloud.skywalking.alarm.model;

public enum ProcessThreadStatus {
    REDISTRIBUTING("1"), REDISTRIBUTE_SUCCESS("2"), FREE("0"), BUSY("3");

    private String value;

    ProcessThreadStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProcessThreadStatus convert(String value) {
        ProcessThreadStatus status;
        switch (value) {
            case "0":
                status = REDISTRIBUTING;
                break;
            case "1":
                status = REDISTRIBUTE_SUCCESS;
                break;
            default:
                throw new IllegalArgumentException("Coordinator status illegal");
        }

        return status;
    }
}
