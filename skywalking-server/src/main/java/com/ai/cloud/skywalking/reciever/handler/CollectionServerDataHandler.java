package com.ai.cloud.skywalking.reciever.handler;

import com.ai.cloud.skywalking.reciever.buffer.DataBufferThreadContainer;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.storage.AlarmRedisConnector;
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
        if (msg != null && msg.length >= 0 && msg.length < Config.DataPackage.MAX_DATA_PACKAGE) {
            // | check sum(4 byte) | data |
            byte[] originCheckSum = new byte[4];
            System.arraycopy(msg, 0, originCheckSum, 0, 4);
            // 对协议进行拆包
            String data = new String(msg, 4, msg.length - 4);
            // 计算校验和
            byte[] checkSum = generateChecksum(data);

            if (Arrays.equals(originCheckSum, checkSum)) {
                DataBufferThreadContainer.getDataBufferThread().saveTemporarily(data.getBytes());
            } else {
                InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().localAddress();
                String key = ctx.name() + "-" + socketAddress.getHostName() + ":" + socketAddress.getPort();
                Jedis jedis = AlarmRedisConnector.getJedis();
                // 如果不存在，则置为0，并且设置生失效时间
                if (jedis.setnx(key, 0 + "") == 1) {
                    jedis.expire(key, Config.Server.EXCEPTION_DATA_SENDING_INTERVAL);
                }

                if (Config.Server.MAX_SEND_EXCEPTION_DATA_COUNT > jedis.incr(key)) {
                    ctx.channel().close();
                }

            }
        }
    }

    /**
     * 生成校验和参数
     *
     * @param data
     * @return
     */
    private byte[] generateChecksum(String data) {
        char[] dataArray = data.toCharArray();
        int result = dataArray[0];
        for (int i = 0; i < dataArray.length; i++) {
            result ^= dataArray[i];
        }

        return intToBytes(result);
    }

    private byte[] intToBytes(int value) {
        byte[] src = new byte[4];
        src[0] = (byte) ((value >> 24) & 0xFF);
        src[1] = (byte) ((value >> 16) & 0xFF);
        src[2] = (byte) ((value >> 8) & 0xFF);
        src[3] = (byte) (value & 0xFF);
        return src;
    }
}
