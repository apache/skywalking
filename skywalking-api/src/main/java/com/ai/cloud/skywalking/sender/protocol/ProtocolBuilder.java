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

    private static byte[] intToByteArray(final int integer) {
        int byteNum = (40 - Integer.numberOfLeadingZeros(integer < 0 ? ~integer : integer)) / 8;
        byte[] byteArray = new byte[4];

        for (int n = 0; n < byteNum; n++)
            byteArray[3 - n] = (byte) (integer >>> (n * 8));

        return (byteArray);
    }
}
