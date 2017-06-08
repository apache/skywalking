package org.skywalking.apm.trace.tag;

/**
 * The tag item with String key and String value.
 *
 * @author wusheng
 */
public final class StringTagItem {
    private String key;
    private String value;

    public StringTagItem(String key, String value) {
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
