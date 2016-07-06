package com.ai.cloud.skywalking.protocol.common;

import com.ai.cloud.skywalking.protocol.NullClass;
import com.ai.cloud.skywalking.serialize.SerializedFactory;
import com.ai.cloud.skywalking.util.IntegerAssist;

import java.util.Arrays;

/**
 * Created by wusheng on 16/7/4.
 */
public abstract class AbstractDataSerializable implements ISerializable, NullableClass {
    public abstract int getDataType();

    public abstract byte[] getData();

    public abstract AbstractDataSerializable convertData(byte[] data);

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
        setPackageLength(messagePackage);
        return messagePackage;
    }

    private void setPackageLength(byte[] messagePackage) {
        byte[] type = IntegerAssist.intToBytes(this.getDataType());
        System.arraycopy(type, 0, messagePackage, 0, type.length);
    }

    private void packData(byte[] messageByteData, byte[] messagePackage) {
        System.arraycopy(messageByteData, 0, messagePackage, 4, messageByteData.length);
    }

    @Override
    public NullableClass convert2Object(byte[] data) {
        int dataType = IntegerAssist.bytesToInt(data, 0);

        if (!SerializedFactory.isCanSerialized(dataType)) {
            return new NullClass();
        }

        return this.convertData(Arrays.copyOfRange(data,4, data.length));
    }


}
