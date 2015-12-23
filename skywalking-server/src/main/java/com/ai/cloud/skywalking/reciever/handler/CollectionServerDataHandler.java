package com.ai.cloud.skywalking.reciever.handler;

import com.ai.cloud.skywalking.reciever.buffer.DataBufferThreadContainer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import static com.ai.cloud.skywalking.reciever.conf.Config.Persistence.*;

public class CollectionServerDataHandler extends SimpleChannelInboundHandler<byte[]> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
        Thread.currentThread().setName("ServerReceiver");
        // 当接受到这条消息的是空，则忽略
        if (msg != null && msg.length >= 0 && msg.length < MAX_STORAGE_SIZE_PER_TIME) {
            DataBufferThreadContainer.getDataBufferThread().saveTemporarily(msg);
        }
    }
}
