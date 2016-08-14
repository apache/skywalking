package com.a.eye.skywalking.reciever.handler;

import com.a.eye.skywalking.reciever.buffer.DataBufferThreadContainer;
import com.a.eye.skywalking.reciever.util.RedisConnector;
import com.a.eye.skywalking.reciever.conf.Config;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import redis.clients.jedis.Jedis;

import java.net.InetSocketAddress;

import static com.a.eye.skywalking.protocol.util.ByteDataUtil.unpackCheckSum;
import static com.a.eye.skywalking.protocol.util.ByteDataUtil.validateCheckSum;

public class CollectionServerDataHandler extends SimpleChannelInboundHandler<byte[]> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
        Thread.currentThread().setName("ServerReceiver");
        // 当接受到这条消息的是空，则忽略
        if (msg != null && msg.length >= 0) {
            if (validateCheckSum(msg)) {
                DataBufferThreadContainer.getDataBufferThread().saveTemporarily(unpackCheckSum(msg));
            } else {
                // 处理错误包
                dealFailedPackage(ctx);
            }
        }
    }


    private void dealFailedPackage(ChannelHandlerContext ctx) {
        InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().localAddress();
        String key = ctx.name() + "-" + socketAddress.getHostName() + ":" + socketAddress.getPort();
        Jedis jedis = RedisConnector.getJedis();
        if (jedis.setnx(key, 0 + "") == 1) {
            jedis.expire(key, Config.Server.FAILED_PACKAGE_WATCHING_TIME_WINDOW);
        }

        if (Config.Server.MAX_WATCHING_FAILED_PACKAGE_SIZE > jedis.incr(key)) {
            ctx.channel().close();
        }
    }


}
