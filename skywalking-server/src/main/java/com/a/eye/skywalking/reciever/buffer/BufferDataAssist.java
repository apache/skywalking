package com.a.eye.skywalking.reciever.buffer;

import com.a.eye.skywalking.protocol.SerializedFactory;
import com.a.eye.skywalking.protocol.common.AbstractDataSerializable;
import com.a.eye.skywalking.protocol.exception.ConvertFailedException;
import com.a.eye.skywalking.protocol.util.IntegerAssist;

import java.util.ArrayList;
import java.util.List;

public class BufferDataAssist {

    private static byte[] SPILT = new byte[] {127, 127, 127, 127};
    private static byte[] EOF   = null;

    private BufferDataAssist() {
        //DO Nothing
    }

    public static byte[] appendLengthAndSplit(byte[] msg) {
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
