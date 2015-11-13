package com.ai.cloud.skywalking.sender.protocol;

public class ProtocolBuilder {

    //协议格式:
    // xx xx xx xx | xx xx xx xx xxx xxx xxx
    //   header(4) |        content
    public static byte[] builder(String data) {
        byte[] content = data.getBytes();
        byte[] header = intToByteArray(content.length);
        byte[] des = new byte[header.length + content.length];
        System.arraycopy(header, 0, des, 0, header.length);
        System.arraycopy(content, 0, des, header.length, content.length);
        return des;
    }

    private static byte[] intToByteArray(final int value) {
        byte[] src = new byte[4];
        src[0] = (byte) ((value >> 24) & 0xFF);
        src[1] = (byte) ((value >> 16) & 0xFF);
        src[2] = (byte) ((value >> 8) & 0xFF);
        src[3] = (byte) (value & 0xFF);
        return src;
    }
}
