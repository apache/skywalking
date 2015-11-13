package com.ai.cloud.skywalking.reciever.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import com.ai.cloud.skywalking.reciever.buffer.DataBufferThreadContainer;

public class CollectionServerDataHandler extends SimpleChannelInboundHandler<byte[]> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
        DataBufferThreadContainer.getDataBufferThread().doCarry(msg);
    }
}
