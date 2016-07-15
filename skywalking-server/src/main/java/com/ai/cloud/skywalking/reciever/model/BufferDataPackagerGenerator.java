package com.ai.cloud.skywalking.reciever.model;

import com.ai.cloud.skywalking.protocol.util.IntegerAssist;

public class BufferDataPackagerGenerator {

    private static byte[] SPILT = new byte[] {127, 127, 127, 127};
    private static byte[] EOF   = null;

    private BufferDataPackagerGenerator() {
        //DO Nothing
    }

    public static byte[] generateEOFPackage() {
        if (EOF != null) {
            return EOF;
        }

        EOF = generatePackage("EOF".getBytes());
        return EOF;
    }

    public static byte[] pack(byte[] msg) {
        return generatePackage(msg);
    }


    private static byte[] generatePackage(byte[] msg) {
        byte[] dataPackage = new byte[msg.length + 8];
        // 前四位长度
        System.arraycopy(IntegerAssist.intToBytes(msg.length), 0, dataPackage, 0, 4);
        // 中间正文
        System.arraycopy(msg, 0, dataPackage, 4, msg.length);
        // 后四位特殊字符
        System.arraycopy(SPILT, 0, dataPackage, msg.length + 4, 4);

        return dataPackage;
    }
}
