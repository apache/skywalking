package com.ai.cloud.skywalking.alarm.model;

public enum ProcessThreadStatus {
    REDISTRIBUTING(1), REDISTRIBUTE_SUCCESS(2), FREE(0), BUSY(3);

    private int value;

    ProcessThreadStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ProcessThreadStatus convert(int value) {
        ProcessThreadStatus status;
        switch (value) {
            case 0:
                status = FREE;
                break;
            case 1:
                status = REDISTRIBUTING;
                break;
            case 2:
                status = REDISTRIBUTE_SUCCESS;
                break;
            case 3:
                status = BUSY;
                break;
            default:
                throw new IllegalArgumentException("Coordinator status illegal");
        }

        return status;
    }
}
