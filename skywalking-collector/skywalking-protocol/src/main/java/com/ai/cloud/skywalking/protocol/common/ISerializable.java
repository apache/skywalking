package com.ai.cloud.skywalking.protocol.common;

import com.ai.cloud.skywalking.protocol.exception.ConvertFailedException;

/**
 * Created by wusheng on 16/7/4.
 */
public interface ISerializable {
    byte[] convert2Bytes();

    NullableClass convert2Object(byte[] data) throws ConvertFailedException;

}
