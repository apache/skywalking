package com.ai.cloud.skywalking.sender;

import com.ai.cloud.skywalking.sender.protocol.ProtocolBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class DataSender {

    private SocketChannel socketChannel;
    private Selector selector;
    private SocketAddress socketAddress;

    public DataSender(String ip, int port) throws IOException {
        selector = Selector.open();
        SocketAddress isa = new InetSocketAddress(ip, port);
        //调用open的静态方法创建连接指定的主机的SocketChannel
        socketChannel = SocketChannel.open(isa);
        //设置该sc已非阻塞的方式工作
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_CONNECT);
        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        this.socketAddress = isa;
    }

    public DataSender(SocketAddress address) throws IOException {
        selector = Selector.open();
        socketChannel = SocketChannel.open(address);
        //设置该sc已非阻塞的方式工作
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_CONNECT);
        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        this.socketAddress = address;
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
            e.printStackTrace();
            // 发送失败 认为不可连接
            DataSenderFactory.unRegister(this);
            return false;
        }
    }

    public SocketAddress getServerIp() {
        return this.socketAddress;
    }

}
