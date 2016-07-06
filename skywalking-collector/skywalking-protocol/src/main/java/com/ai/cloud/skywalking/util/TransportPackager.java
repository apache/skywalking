package com.ai.cloud.skywalking.util;

import com.ai.cloud.skywalking.protocol.common.ISerializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TransportPackager {
    public static byte[] pack(List<ISerializable> beSendingData) {
        // 对协议格式进行修改
        // | check sum(4 byte) |  data
        byte[] dataText = packDataText(beSendingData);
        byte[] dataPackage = packCheckSum(dataText);
        return dataPackage;
    }

    public static List<byte[]> unpack(byte[] dataPackage) {
        if (validateCheckSum(dataPackage)) {
            return unpackDataText(dataPackage);
        } else {
            return new ArrayList<byte[]>();
        }
    }

    private static List<byte[]> unpackDataText(byte[] dataPackage) {
        List<byte[]> serializeData = new ArrayList<byte[]>();
        int currentLength = 0;
        while (true) {
            //读取长度
            int dataLength = IntegerAssist.bytesToInt(dataPackage, 0);
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
        int result = data[0];
        for (int i = offset; i < data.length; i++) {
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

    private static byte[] packDataText(List<ISerializable> beSendingData) {
        byte[] dataText = new byte[1024 * 30];
        int currentIndex = 0;
        for (ISerializable sendingData : beSendingData) {
            byte[] dataElementText = appendingLength(sendingData.convert2Bytes());
            dataText = expansionCapacityIfNecessary(dataText, currentIndex, dataElementText);
            appendBeSendingDataText(dataElementText, dataText, currentIndex);
            currentIndex += dataElementText.length;
        }

        return dataText;
    }

    private static void appendBeSendingDataText(byte[] beSendingDataText, byte[] dataText, int currentIndex) {
        System.arraycopy(beSendingDataText, 0, dataText, currentIndex, beSendingDataText.length);
    }

    private static byte[] expansionCapacityIfNecessary(byte[] dataText, int currentLength, byte[] beSendingDataText) {
        if (beSendingDataText.length + currentLength > 1024 * 30) {
            byte[] newDataText = new byte[dataText.length + 1024 * 30];
            System.arraycopy(dataText, 0, newDataText, 0, dataText.length);
            return newDataText;
        }
        return dataText;
    }

    private static byte[] appendingLength(byte[] dataByte) {
        byte[] dataText = new byte[dataByte.length + 4];
        System.arraycopy(dataByte, 0, dataText, 4, dataByte.length);
        byte[] length = IntegerAssist.intToBytes(dataByte.length);
        System.arraycopy(length, 0, dataText, 0, 4);
        return dataText;
    }
}
