package org.skywalking.apm.agent.core.context.tag;

/**
 * The tag item with String key and Boolean value.
 *
 * @author wusheng
 */
public class BooleanTagItem {
    private String key;
    private boolean value;

    public BooleanTagItem(String key, boolean value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public boolean getValue() {
        return value;
    }
}
