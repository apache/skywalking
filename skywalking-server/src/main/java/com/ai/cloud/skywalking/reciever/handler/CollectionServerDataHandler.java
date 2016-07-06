package com.ai.cloud.skywalking.reciever.handler;

import com.ai.cloud.skywalking.reciever.buffer.DataBufferThreadContainer;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.util.RedisConnector;
import com.ai.cloud.skywalking.util.TransportPackager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import redis.clients.jedis.Jedis;

import java.net.InetSocketAddress;
import java.util.List;

public class CollectionServerDataHandler extends SimpleChannelInboundHandler<byte[]> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
        Thread.currentThread().setName("ServerReceiver");
        // 当接受到这条消息的是空，则忽略
        if (msg != null && msg.length >= 0) {

            List<byte[]> byteSerializeObjects = TransportPackager.unpack(msg);

            if (byteSerializeObjects.size() > 0) {
                cacheSerializeObjects(byteSerializeObjects);
            } else {
                // 处理错误包
                dealFailedPackage(ctx);
            }
        }
    }

    private void cacheSerializeObjects(List<byte[]> byteSerializeObjects) {
        for (byte[] byteSerializeObject : byteSerializeObjects) {
            DataBufferThreadContainer.getDataBufferThread().saveTemporarily(byteSerializeObject);
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
