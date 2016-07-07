package com.ai.cloud.skywalking.protocol;

import com.ai.cloud.skywalking.protocol.common.ISerializable;
import com.ai.cloud.skywalking.protocol.util.IntegerAssist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TransportPackager {
    public static byte[] pack(List<ISerializable> beSendingData) {
        // 对协议格式进行修改
        // | check sum(4 byte) |  data
        byte[] dataText = packDataBody(beSendingData);
        byte[] dataPackage = packCheckSum(dataText);
        return dataPackage;
    }

    public static List<byte[]> unpack(byte[] dataPackage) {
        if (validateCheckSum(dataPackage)) {
            return unpackDataBody(unpackCheckSum(dataPackage));
        } else {
            return new ArrayList<byte[]>();
        }
    }

    private static byte[] unpackCheckSum(byte[] dataPackage) {
        return Arrays.copyOfRange(dataPackage, 4, dataPackage.length);
    }

    private static List<byte[]> unpackDataBody(byte[] dataPackage) {
        List<byte[]> serializeData = new ArrayList<byte[]>();
        int currentLength = 0;
        while (true) {
            //读取长度
            int dataLength = IntegerAssist.bytesToInt(dataPackage, currentLength);
            // 反序列化
            byte[] data = new byte[dataLength];
            System.arraycopy(dataPackage, currentLength + 4, data, 0, dataLength);
            //
            serializeData.add(data);
            currentLength = 4 + dataLength;
            if (currentLength >= dataPackage.length) {
                break;
            }
        }
        return serializeData;
    }

    private static boolean validateCheckSum(byte[] dataPackage) {
        byte[] checkSum = generateChecksum(dataPackage, 4);
        byte[] originCheckSum = new byte[4];
        System.arraycopy(dataPackage, 0, originCheckSum, 0, 4);
        return Arrays.equals(checkSum, originCheckSum);
    }

    /**
     * 生成校验和参数
     *
     * @param data
     * @return
     */
    private static byte[] generateChecksum(byte[] data, int offset) {
        int result = data[offset];
        for (int i = offset + 1; i < data.length; i++) {
            result ^= data[i];
        }

        return IntegerAssist.intToBytes(result);
    }

    private static byte[] packCheckSum(byte[] dataText) {
        byte[] dataPackage = new byte[dataText.length + 4];
        byte[] checkSum = generateChecksum(dataText, 0);
        System.arraycopy(checkSum, 0, dataPackage, 0, 4);
        System.arraycopy(dataText, 0, dataPackage, 4, dataText.length);
        return dataPackage;
    }

    private static byte[] packDataBody(List<ISerializable> beSendingData) {
        byte[] dataText = null;
        int currentIndex = 0;
        for (ISerializable sendingData : beSendingData) {
            byte[] dataElementText = setElementPackageLength(sendingData.convert2Bytes());
            if (dataText == null) {
                dataText = new byte[dataElementText.length];
            } else {
                dataText = Arrays.copyOf(dataText, dataText.length + dataElementText.length);
            }
            System.arraycopy(dataElementText, 0, dataText, currentIndex, dataElementText.length);
            currentIndex += dataElementText.length;
        }

        return dataText;
    }

    private static byte[] setElementPackageLength(byte[] dataByte) {
        byte[] dataText = new byte[dataByte.length + 4];
        System.arraycopy(dataByte, 0, dataText, 4, dataByte.length);
        byte[] length = IntegerAssist.intToBytes(dataByte.length);
        System.arraycopy(length, 0, dataText, 0, 4);
        return dataText;
    }
}
