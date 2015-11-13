package com.ai.cloud.skywalking.reciever.handler;

import com.ai.cloud.skywalking.reciever.buffer.DataBufferThreadContainer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class CollectionServerDataHandler extends SimpleChannelInboundHandler {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        DataBufferThreadContainer.getDataBufferThread().doCarry(msg.toString().getBytes());
    }
}
