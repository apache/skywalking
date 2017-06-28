package org.skywalking.apm.agent.core.dictionary;

/**
 * @author wusheng
 */
public class DictionaryValueHolder {
    private int value = DictionaryUtil.nullValue();

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
