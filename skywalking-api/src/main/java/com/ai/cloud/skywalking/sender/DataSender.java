package com.ai.cloud.skywalking.sender;

import com.ai.cloud.skywalking.sender.protocol.ProtocolBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class DataSender {
    private SocketChannel socketChannel;
    private Selector selector;
    private InetSocketAddress socketAddress;
    private SenderStatus status = SenderStatus.FAILED;

    public DataSender(String ip, int port) throws IOException {
        selector = Selector.open();
        InetSocketAddress isa = new InetSocketAddress(ip, port);
        //调用open的静态方法创建连接指定的主机的SocketChannel
        socketChannel = SocketChannel.open(isa);
        //设置该sc已非阻塞的方式工作
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_CONNECT);
        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        this.socketAddress = isa;
        status = SenderStatus.READY;
    }

    public DataSender(InetSocketAddress address) throws IOException {
        selector = Selector.open();
        socketChannel = SocketChannel.open(address);
        //设置该sc已非阻塞的方式工作
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_CONNECT);
        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        this.socketAddress = address;
        status = SenderStatus.READY;
    }

    /**
     * 返回是否发送成功
     *
     * @param data
     * @return
     */
    public boolean send(String data) {
        // 发送报文
        try {
            socketChannel.register(selector, SelectionKey.OP_READ);
            socketChannel.write(ByteBuffer.wrap(ProtocolBuilder.builder(data)));
            return true;
        } catch (IOException e) {
            // 发送失败 认为不可连接
            DataSenderFactoryWithBalance.unRegister(this);
            return false;
        }
    }

    public InetSocketAddress getServerIp() {
        return this.socketAddress;
    }

    public void closeConnect() throws IOException {
        if (socketChannel != null) {
            socketChannel.close();
        }
    }

    public enum SenderStatus {
        READY, FAILED, SWITCHING
    }

    public SenderStatus getStatus() {
        return status;
    }

    public void setStatus(SenderStatus status) {
        this.status = status;
    }
}
