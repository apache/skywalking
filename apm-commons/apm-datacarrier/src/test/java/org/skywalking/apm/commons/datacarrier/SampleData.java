package org.skywalking.apm.commons.datacarrier;

/**
 * Created by wusheng on 2016/10/25.
 */
public class SampleData {
    private int intValue;

    private String name;

    public int getIntValue() {
        return intValue;
    }

    public String getName() {
        return name;
    }

    public SampleData setIntValue(int intValue) {
        this.intValue = intValue;
        return this;
    }

    public SampleData setName(String name) {
        this.name = name;
        return this;
    }
}
