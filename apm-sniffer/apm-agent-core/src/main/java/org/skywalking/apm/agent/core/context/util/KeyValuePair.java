package org.skywalking.apm.agent.core.context.util;

import org.skywalking.apm.network.proto.KeyWithStringValue;

/**
 * The <code>KeyValuePair</code> represents a object which contains a string key and a string value.
 *
 * @author wusheng
 */
public class KeyValuePair {
    private String key;
    private String value;

    public KeyValuePair(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public KeyWithStringValue transform() {
        KeyWithStringValue.Builder keyValueBuilder = KeyWithStringValue.newBuilder();
        keyValueBuilder.setKey(key);
        if (value != null) {
            keyValueBuilder.setValue(value);
        }
        return keyValueBuilder.build();
    }
}
