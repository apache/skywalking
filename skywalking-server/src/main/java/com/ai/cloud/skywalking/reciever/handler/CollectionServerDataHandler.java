package com.ai.cloud.skywalking.reciever.handler;

import com.ai.cloud.skywalking.reciever.buffer.DataBufferThreadContainer;
import com.ai.cloud.skywalking.reciever.conf.Config;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class CollectionServerDataHandler extends SimpleChannelInboundHandler<byte[]> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
        Thread.currentThread().setName("ServerReceiver");
        // 当接受到这条消息的是空，则忽略
        if (msg != null && msg.length >= 0 && msg.length < Config.DataPackage.MAX_DATA_PACKAGE) {
            int start = 0;
            int end;
            while (start < msg.length) {
                int length = bytesToInt(msg, start);
                start = start + 4;
                end = start + length;
                byte[] dest = new byte[length];
                System.arraycopy(msg, start, dest, 0, length);
                DataBufferThreadContainer.getDataBufferThread().saveTemporarily(dest);
                start = end;
            }
        }
    }

    public static int bytesToInt(byte[] ary, int offset) {
        int value;
        value = (int) ((ary[offset + 3] & 0xFF)
                | ((ary[offset + 2] << 8) & 0xFF00)
                | ((ary[offset + 1] << 16) & 0xFF0000)
                | ((ary[offset] << 24) & 0xFF000000));
        return value;
    }
}
