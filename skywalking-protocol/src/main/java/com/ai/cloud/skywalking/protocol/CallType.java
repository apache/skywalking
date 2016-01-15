package com.ai.cloud.skywalking.protocol;

public enum CallType {

    LOCAL('L'), SYNC('S'), ASYNC('A');

    private char value;

    CallType(char value) {
        this.value = value;
    }

    public CallType convert(String id) {
        char v = id.charAt(0);
        switch (v) {
            case 'L':
                return LOCAL;
            case 'S':
                return SYNC;
            case 'A':
                return ASYNC;
            default:
                throw new IllegalStateException("Failed to convert callType[" + id + "]");
        }
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
