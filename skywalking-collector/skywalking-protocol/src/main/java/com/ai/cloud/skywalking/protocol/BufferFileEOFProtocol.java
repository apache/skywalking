package com.ai.cloud.skywalking.protocol;

import com.ai.cloud.skywalking.protocol.common.AbstractDataSerializable;
import com.ai.cloud.skywalking.protocol.exception.ConvertFailedException;

public class BufferFileEOFProtocol extends AbstractDataSerializable {
    @Override
    public int getDataType() {
        return -1;
    }

    @Override
    public byte[] getData() {
        return new byte[0];
    }

    @Override
    public AbstractDataSerializable convertData(byte[] data) throws ConvertFailedException {
        return new BufferFileEOFProtocol();
    }

    @Override
    public boolean isNull() {
        return false;
    }
}
