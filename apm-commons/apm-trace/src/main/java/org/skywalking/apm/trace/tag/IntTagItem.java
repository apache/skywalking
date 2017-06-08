package org.skywalking.apm.trace.tag;

/**
 * The tag item with String key and Int value.
 *
 * @author wusheng
 */
public class IntTagItem {
    private String key;
    private int value;

    public IntTagItem(String key, int value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public int getValue() {
        return value;
    }
}
