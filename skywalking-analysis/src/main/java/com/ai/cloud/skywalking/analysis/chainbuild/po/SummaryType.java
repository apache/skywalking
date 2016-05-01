package com.ai.cloud.skywalking.analysis.chainbuild.po;

public enum SummaryType {
    MINUTER('m'), HOUR('H'), DAY('D'), MONTH('M');

    private char value;

    SummaryType(char value) {
        this.value = value;
    }

    public static SummaryType convert(String value) {
        char valueChar = value.charAt(0);
        switch (valueChar) {
            case 'm':
                return MINUTER;
            case 'H':
                return HOUR;
            case 'D':
                return DAY;
            case 'M':
                return MONTH;
            default:
                throw new RuntimeException("Can not find the summary type[" + value + "]");
        }
    }

    public char getValue() {
        return value;
    }
}
