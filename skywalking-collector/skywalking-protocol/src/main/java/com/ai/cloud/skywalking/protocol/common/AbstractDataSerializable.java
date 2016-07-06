package com.ai.cloud.skywalking.protocol.common;

import com.ai.cloud.skywalking.protocol.NullClass;
import com.ai.cloud.skywalking.serialize.SerializedFactory;
import com.ai.cloud.skywalking.util.IntegerAssist;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by wusheng on 16/7/4.
 */
public abstract class AbstractDataSerializable implements ISerializable, NullableClass {
    private static Set<Integer> DATA_TYPE_SCOPE = new HashSet<Integer>();

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
        byte[] messagePackage = new byte[4 + getData().length];
        appendingDataText(messagePackage);
        appendingDataLength(messagePackage);
        return messagePackage;
    }

    private void appendingDataLength(byte[] dataByte) {
        byte[] type = IntegerAssist.intToBytes(this.getDataType());
        System.arraycopy(type, 0, dataByte, 0, type.length);
    }

    private byte[] appendingDataText(byte[] dataByte) {
        System.arraycopy(getData(), 0, dataByte, 4, dataByte.length);
        return dataByte;
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
