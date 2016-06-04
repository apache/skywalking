package com.ai.cloud.skywalking.reciever.handler;

import com.ai.cloud.skywalking.reciever.buffer.DataBufferThreadContainer;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.util.RedisConnector;
import com.ai.cloud.skywalking.util.ProtocolPackager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import redis.clients.jedis.Jedis;

import java.net.InetSocketAddress;

public class CollectionServerDataHandler extends SimpleChannelInboundHandler<byte[]> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
        Thread.currentThread().setName("ServerReceiver");
        // 当接受到这条消息的是空，则忽略
        if (msg != null && msg.length >= 0 && msg.length < Config.DataPackage.MAX_DATA_PACKAGE) {

            byte[] data = ProtocolPackager.unpack(msg);

            if (data != null) {
                DataBufferThreadContainer.getDataBufferThread().saveTemporarily(data);
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
