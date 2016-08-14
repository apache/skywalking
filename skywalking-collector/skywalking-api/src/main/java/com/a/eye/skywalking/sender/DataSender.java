package com.a.eye.skywalking.sender;

import com.a.eye.skywalking.selfexamination.HeathReading;
import com.a.eye.skywalking.selfexamination.SDKHealthCollector;
import com.a.eye.skywalking.protocol.common.ISerializable;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import com.a.eye.skywalking.protocol.TransportPackager;

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
            SDKHealthCollector
                    .getCurrentHeathReading("sender").updateData(HeathReading.INFO, "DataSender[" + socketAddress + "] send data successfully.");
        }
    }

    /**
     * 返回是否发送成功
     *
     * @param packageData
     * @return
     */
    @Override
    public boolean send(List<ISerializable> packageData) {
        try {
            if (channel != null && channel.isActive()) {

                byte[] dataPackage = TransportPackager.pack(packageData);
                channel.writeAndFlush(dataPackage);

                SDKHealthCollector.getCurrentHeathReading("sender").updateData(HeathReading.INFO, "DataSender[" + socketAddress + "] send data successfully.");
                return true;
            }else{
                DataSenderFactoryWithBalance.unRegister(this);
                SDKHealthCollector.getCurrentHeathReading("sender").updateData(HeathReading.WARNING, "DataSender[" + socketAddress + "] channel isn't active. unregister sender.");
            }
        } catch (Exception e) {
            DataSenderFactoryWithBalance.unRegister(this);
            SDKHealthCollector.getCurrentHeathReading("sender").updateData(HeathReading.WARNING, "DataSender[" + socketAddress + "] channel broken. unregister sender.");
        }

        return false;
    }

    public InetSocketAddress getServerAddr() {
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
