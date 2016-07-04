package com.ai.cloud.skywalking.util;

import com.ai.cloud.skywalking.protocol.common.ISerializable;

import java.util.Arrays;
import java.util.List;

public class TransportPackager {
    public static byte[] pack(List<ISerializable> packageData) {
        // 对协议格式进行修改
        // | check sum(4 byte) |  data
        byte[] dataPackage = new byte[data.length + 4];

        packDataText(data, dataPackage);
        packCheckSum(data, dataPackage);

        return dataPackage;
    }

    private static void packCheckSum(byte[] data, byte[] dataPackage) {
        byte[] checkSumArray = generateChecksum(data, 0);
        System.arraycopy(checkSumArray, 0, dataPackage, 0, 4);
    }

    private static void packDataText(byte[] data, byte[] dataPackage) {
        System.arraycopy(data, 0, dataPackage, 4, data.length);
    }


    public static List<ISerializable> unpack(byte[] dataPackage) {
        if (validateCheckSum(dataPackage)) {
            return unpackDataText(dataPackage);
        } else {
            return null;
        }

    }

    private static byte[] unpackDataText(byte[] dataPackage) {
        byte[] data = new byte[dataPackage.length - 4];
        System.arraycopy(dataPackage, 4, data, 0, data.length);
        return data;
    }

    private static boolean validateCheckSum(byte[] dataPackage){
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
}
