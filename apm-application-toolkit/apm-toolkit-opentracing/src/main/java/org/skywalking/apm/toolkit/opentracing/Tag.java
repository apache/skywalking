package org.skywalking.apm.toolkit.opentracing;

/**
 * @author wusheng
 */
public class Tag {
    private String key;
    private String value;

    public Tag(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
