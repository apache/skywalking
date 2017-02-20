package com.a.eye.skywalking.api.util;

/**
 * Created data xin on 2016/12/4.
 */
public class RoutingKeyGenerator {

    public static int generate(String originData) {
        char[] value = originData.toCharArray();
        int h = 0;
        if (h == 0 && value.length > 0) {
            char val[] = value;

            for (int i = 0; i < value.length; i++) {
                h = 31 * h + val[i];
            }
        }
        return h;
    }
}
