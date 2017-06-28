package org.skywalking.apm.agent.core.dictionary;

/**
 * @author wusheng
 */
public class DictionaryUtil {
    public static int nullValue() {
        return -1;
    }

    public static boolean isNull(int value) {
        return value == nullValue();
    }

    public static boolean isNull(String text) {
        return text == null;
    }
}
