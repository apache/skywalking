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
            DataBufferThreadContainer.getDataBufferThread().saveTemporarily(msg);
        }
    }
}
