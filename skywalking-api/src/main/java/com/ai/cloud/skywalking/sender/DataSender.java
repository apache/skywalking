package com.ai.cloud.skywalking.sender;

import com.ai.cloud.io.netty.bootstrap.Bootstrap;
import com.ai.cloud.io.netty.channel.*;
import com.ai.cloud.io.netty.channel.nio.NioEventLoopGroup;
import com.ai.cloud.io.netty.channel.socket.ServerSocketChannel;
import com.ai.cloud.io.netty.channel.socket.nio.NioSocketChannel;
import com.ai.cloud.io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import com.ai.cloud.io.netty.handler.codec.LengthFieldPrepender;
import com.ai.cloud.io.netty.handler.codec.bytes.ByteArrayDecoder;
import com.ai.cloud.io.netty.handler.codec.bytes.ByteArrayEncoder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class DataSender extends ChannelInboundHandlerAdapter implements IDataSender {
    private static Logger logger = Logger.getLogger(DataSender.class.getName());
    private EventLoopGroup group;
    private SenderStatus status = SenderStatus.FAILED;
    private InetSocketAddress socketAddress;
    private ChannelFuture channelFuture;

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
                    .handler(new ChannelInitializer<ServerSocketChannel>() {
                        @Override
                        protected void initChannel(ServerSocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                            p.addLast("frameEncoder", new LengthFieldPrepender(4));
                            p.addLast("decoder", new ByteArrayDecoder());
                            p.addLast("encoder", new ByteArrayEncoder());
                        }
                    });
            channelFuture = bootstrap.connect(address).sync();
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
            if (channelFuture != null) {
                channelFuture.channel().writeAndFlush(data);
                return true;
            }
        } catch (Exception e) {
            DataSenderFactoryWithBalance.unRegister(this);
        }

        return false;
    }

    public InetSocketAddress getServerIp() {
        return this.socketAddress;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        close();
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
