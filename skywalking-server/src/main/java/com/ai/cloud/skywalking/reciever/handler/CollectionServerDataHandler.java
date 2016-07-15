package com.ai.cloud.skywalking.reciever.handler;

import com.ai.cloud.skywalking.protocol.util.IntegerAssist;
import com.ai.cloud.skywalking.reciever.buffer.DataBufferThreadContainer;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.util.RedisConnector;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import redis.clients.jedis.Jedis;

import java.net.InetSocketAddress;
import java.util.Arrays;

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

    private byte[] unpackCheckSum(byte[] msg) {
        return Arrays.copyOfRange(msg, 4, msg.length);
    }

    private boolean validateCheckSum(byte[] dataPackage) {
        byte[] checkSum = generateChecksum(dataPackage, 4);
        byte[] originCheckSum = new byte[4];
        System.arraycopy(dataPackage, 0, originCheckSum, 0, 4);
        return Arrays.equals(checkSum, originCheckSum);
    }

    private static byte[] generateChecksum(byte[] data, int offset) {
        int result = data[offset];
        for (int i = offset + 1; i < data.length; i++) {
            result ^= data[i];
        }

        return IntegerAssist.intToBytes(result);
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
