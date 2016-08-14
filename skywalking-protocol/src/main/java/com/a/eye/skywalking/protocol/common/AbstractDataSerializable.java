package com.a.eye.skywalking.protocol.common;

import com.a.eye.skywalking.protocol.NullClass;
import com.a.eye.skywalking.protocol.SerializedFactory;
import com.a.eye.skywalking.protocol.exception.ConvertFailedException;
import com.a.eye.skywalking.protocol.util.IntegerAssist;

import java.util.Arrays;

/**
 * Created by wusheng on 16/7/4.
 */
public abstract class AbstractDataSerializable implements ISerializable, NullableClass {
    public abstract int getDataType();

    public abstract byte[] getData();

    public abstract AbstractDataSerializable convertData(byte[] data) throws ConvertFailedException;

    /**
     * 消息包结构:
     * 4位消息体类型
     * n位数据正文
     *
     * @return
     */
    @Override
    public byte[] convert2Bytes() {
        byte[] messageByteData = getData();
        byte[] messagePackage = new byte[4 + messageByteData.length];
        packData(messageByteData, messagePackage);
        setProtocolType(messageByteData, messagePackage);
        return messagePackage;
    }

    private void setProtocolType(byte[] messageByteData, byte[] messagePackage) {
        System.arraycopy(IntegerAssist.intToBytes(getDataType()), 0, messagePackage, 0, 4);
    }

    private void packData(byte[] messageByteData, byte[] messagePackage) {
        System.arraycopy(messageByteData, 0, messagePackage, 4, messageByteData.length);
    }

    @Override
    public NullableClass convert2Object(byte[] data) throws ConvertFailedException {
        int dataType = IntegerAssist.bytesToInt(data, 0);

        if (!SerializedFactory.isCanSerialized(dataType)) {
            return new NullClass();
        }

        return this.convertData(Arrays.copyOfRange(data, 4, data.length));
    }


}
