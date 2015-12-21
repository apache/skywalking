package com.ai.cloud.skywalking.sender;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ai.cloud.skywalking.sender.protocol.ProtocolBuilder;

public class DataSender implements IDataSender{
	private static Logger logger = Logger.getLogger(DataSender.class.getName());
	
    private SocketChannel socketChannel;
    private Selector selector;
    private InetSocketAddress socketAddress;
    private SenderStatus status = SenderStatus.FAILED;

    public DataSender(String ip, int port) throws IOException {
    	this(new InetSocketAddress(ip, port));
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
    @Override
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

    public void close(){
        if (socketChannel != null) {
            try {
				socketChannel.close();
			} catch (IOException e) {
				logger.log(Level.ALL, "close connection Failed");
			}
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
