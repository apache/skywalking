package com.ai.cloud.skywalking.reciever;

import com.ai.cloud.skywalking.reciever.buffer.DataBufferThread;
import com.ai.cloud.skywalking.reciever.buffer.DataBufferThreadContainer;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.conf.ConfigInitializer;
import com.ai.cloud.skywalking.reciever.persistance.PersistenceThreadLauncher;
import com.ai.cloud.skywalking.reciever.util.ByteArrayUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Properties;

public class CollectionServer {

    static Logger logger = LogManager.getLogger(CollectionServer.class);
    private Selector selector;

    public CollectionServer() {
    }

    public void doCollect() throws IOException {
        ServerSocketChannel serverSocketChannel = initServerSocketChannel();
        DataBufferThread dataBuffer;
        while (selector.select() > 0) {
            Iterator iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = (SelectionKey) iterator.next();
                iterator.remove();
                beginToRead(serverSocketChannel, key);
                if (key.isReadable()) {
                    ByteChannel sc = (SocketChannel) key.channel();
                    ByteBuffer contextLengthBuffer = ByteBuffer.allocate(4);
                    try {
                        sc.read(contextLengthBuffer);
                        int length = ByteArrayUtil.byteArrayToInt(contextLengthBuffer.array(), 0);
                        if (length > 0) {
                            ByteBuffer contentBuffer = ByteBuffer.allocate(length);
                            sc.read(contentBuffer);
                            dataBuffer = DataBufferThreadContainer.getDataBufferThread();
                            dataBuffer.doCarry(new String(contentBuffer.array()));
                        }
                        contextLengthBuffer.flip();
                    } catch (IOException e) {
                        logger.error("The remote client disconnect service", e);
                        sc.close();
                    }
                }
            }
        }
    }

    private void beginToRead(ServerSocketChannel serverSocketChannel, SelectionKey key) throws IOException {
        if (key.isAcceptable()) {
            SocketChannel sc = serverSocketChannel.accept();
            sc.configureBlocking(false);
            sc.register(selector, SelectionKey.OP_READ);
        }
    }

    private ServerSocketChannel initServerSocketChannel() throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("0.0.0.0", Config.Server.PORT));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        logger.info("The service is listening on port {}", Config.Server.PORT);
        return serverSocketChannel;
    }


    public static void main(String[] args) throws IOException, IllegalAccessException {
        logger.info("To initialize the collect server configuration parameters....");
        initializeParam();
        logger.info("To launch register persistence thread....");
        PersistenceThreadLauncher.doLaunch();
        logger.info("To init data buffer thread container...");
        DataBufferThreadContainer.init();
        logger.info("Starting collection server.....");
        new CollectionServer().doCollect();
    }

    private static void initializeParam() throws IllegalAccessException, IOException {
        Properties properties = new Properties();
        try {
            properties.load(CollectionServer.class.getResourceAsStream("/config.properties"));
            ConfigInitializer.initialize(properties, Config.class);
        } catch (IllegalAccessException e) {
            logger.error("Initialize the collect server configuration failed", e);
            throw e;
        } catch (IOException e) {
            logger.error("Initialize the collect server configuration failed", e);
            throw e;
        }
    }

}
