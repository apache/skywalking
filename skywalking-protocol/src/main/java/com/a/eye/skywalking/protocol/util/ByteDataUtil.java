package com.a.eye.skywalking.protocol.util;


import java.util.Arrays;

public class ByteDataUtil {
    public static byte[] unpackCheckSum(byte[] msg) {
        return Arrays.copyOfRange(msg, 4, msg.length);
    }


    public static boolean validateCheckSum(byte[] dataPackage) {
        byte[] checkSum = generateChecksum(dataPackage, 4);
        byte[] originCheckSum = new byte[4];
        System.arraycopy(dataPackage, 0, originCheckSum, 0, 4);
        return Arrays.equals(checkSum, originCheckSum);
    }


    public static byte[] generateChecksum(byte[] data, int offset) {
        int result = data[offset];
        for (int i = offset + 1; i < data.length; i++) {
            result ^= data[i];
        }

        return IntegerAssist.intToBytes(result);
    }
}
