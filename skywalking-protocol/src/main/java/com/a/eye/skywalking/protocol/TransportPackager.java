package com.a.eye.skywalking.protocol;

import com.a.eye.skywalking.protocol.util.ByteDataUtil;
import com.a.eye.skywalking.protocol.util.IntegerAssist;
import com.a.eye.skywalking.protocol.common.ISerializable;

import java.util.Arrays;
import java.util.List;

public class TransportPackager {
    
    public static byte[] pack(List<ISerializable> beSendingData) {
        // 对协议格式进行修改
        // | check sum(4 byte) |  data
        byte[] data = serializeObjects(beSendingData);
        byte[] dataPackage = appendCheckSum(data);
        return dataPackage;
    }



    private static byte[] appendCheckSum(byte[] dataText) {
        byte[] dataPackage = new byte[dataText.length + 4];
        byte[] checkSum = ByteDataUtil.generateChecksum(dataText, 0);
        System.arraycopy(checkSum, 0, dataPackage, 0, 4);
        System.arraycopy(dataText, 0, dataPackage, 4, dataText.length);
        return dataPackage;
    }


    private static byte[] serializeObjects(List<ISerializable> beSendingData) {
        byte[] data = null;
        int currentIndex = 0;
        for (ISerializable sendingData : beSendingData) {
            byte[] elementData = serialize(sendingData);
            data = appendData(data, currentIndex, elementData);
            currentIndex += elementData.length;
        }

        return data;
    }

    private static byte[] appendData(byte[] dataText, int currentIndex, byte[] dataElementText) {
        if (dataText == null) {
            dataText = new byte[dataElementText.length];
        } else {
            dataText = Arrays.copyOf(dataText, dataText.length + dataElementText.length);
        }
        System.arraycopy(dataElementText, 0, dataText, currentIndex, dataElementText.length);
        return dataText;
    }


    public static byte[] serialize(ISerializable serializable) {
        byte[] serializableBytes = serializable.convert2Bytes();
        byte[] dataText = new byte[serializableBytes.length  + 4];
        System.arraycopy(serializableBytes, 0, dataText, 4, serializableBytes.length);
        byte[] length = IntegerAssist.intToBytes(serializableBytes.length);
        System.arraycopy(length, 0, dataText, 0, 4);
        return dataText;
    }
}
