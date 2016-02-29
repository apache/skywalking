package com.ai.cloud.skywalking.sender;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.ai.cloud.io.netty.bootstrap.Bootstrap;
import com.ai.cloud.io.netty.channel.Channel;
import com.ai.cloud.io.netty.channel.ChannelHandlerContext;
import com.ai.cloud.io.netty.channel.ChannelInboundHandlerAdapter;
import com.ai.cloud.io.netty.channel.ChannelInitializer;
import com.ai.cloud.io.netty.channel.ChannelOption;
import com.ai.cloud.io.netty.channel.ChannelPipeline;
import com.ai.cloud.io.netty.channel.EventLoopGroup;
import com.ai.cloud.io.netty.channel.nio.NioEventLoopGroup;
import com.ai.cloud.io.netty.channel.socket.SocketChannel;
import com.ai.cloud.io.netty.channel.socket.nio.NioSocketChannel;
import com.ai.cloud.io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import com.ai.cloud.io.netty.handler.codec.LengthFieldPrepender;
import com.ai.cloud.io.netty.handler.codec.bytes.ByteArrayDecoder;
import com.ai.cloud.io.netty.handler.codec.bytes.ByteArrayEncoder;
import com.ai.cloud.skywalking.selfexamination.HeathReading;
import com.ai.cloud.skywalking.selfexamination.SDKHealthCollector;

public class DataSender implements IDataSender {
    private EventLoopGroup group;
    private SenderStatus status = SenderStatus.FAILED;
    private InetSocketAddress socketAddress;
    private Channel channel;

    public DataSender(String ip, int port) throws IOException {
        this(new InetSocketAddress(ip, port));
    }

    public DataSender(InetSocketAddress address) throws IOException {
        this.socketAddress = address;
        status = SenderStatus.READY;
        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                            p.addLast("frameEncoder", new LengthFieldPrepender(4));
                            p.addLast("decoder", new ByteArrayDecoder());
                            p.addLast("encoder", new ByteArrayEncoder());
                            p.addLast(new ChannelInboundHandlerAdapter() {
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    super.channelActive(ctx);
                                    channel = ctx.channel();
                                }
                            });
                        }
                    });
            bootstrap.connect(address).sync();
        } catch (Exception e) {
            status = SenderStatus.FAILED;
        }
    }

    /**
     * 返回是否发送成功
     *
     * @param data
     * @return
     */
    @Override
    public boolean send(String data) {
        try {
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(data.getBytes());
                SDKHealthCollector.getCurrentHeathReading("sender").updateData(HeathReading.INFO, "DataSender send data successfully.");
                return true;
            }else{
                DataSenderFactoryWithBalance.unRegister(this);
                SDKHealthCollector.getCurrentHeathReading("sender").updateData(HeathReading.WARNING, "DataSender channel isn't active. unregister sender.");
            }
        } catch (Exception e) {
            DataSenderFactoryWithBalance.unRegister(this);
            SDKHealthCollector.getCurrentHeathReading("sender").updateData(HeathReading.WARNING, "DataSender channel broken. unregister sender.");
        }

        return false;
    }

    public InetSocketAddress getServerIp() {
        return this.socketAddress;
    }

    public void close() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    public enum SenderStatus {
        READY, FAILED
    }

    public SenderStatus getStatus() {
        return status;
    }

    public void setStatus(SenderStatus status) {
        this.status = status;
    }
}
