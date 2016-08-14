package com.a.eye.skywalking.protocol;

import com.a.eye.skywalking.protocol.common.NullableClass;

/**
 * Created by wusheng on 16/7/4.
 */
public class NullClass implements NullableClass {
    @Override
    public boolean isNull() {
        return true;
    }
}
