package com.a.eye.skywalking.protocol;

import com.a.eye.skywalking.protocol.common.AbstractDataSerializable;
import com.a.eye.skywalking.protocol.exception.ConvertFailedException;

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

    public boolean isNull() {
        return false;
    }
}
