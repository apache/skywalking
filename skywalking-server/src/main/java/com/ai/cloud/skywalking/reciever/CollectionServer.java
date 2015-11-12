package com.ai.cloud.skywalking.reciever;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class CollectionServer {

    static Logger logger = LogManager.getLogger(CollectionServer.class);
    private Selector selector;
    static Map<Integer, ByteBuffer> byteBuffers = new LinkedHashMap<Integer, ByteBuffer>();


    public CollectionServer() {
        byteBuffers.put(512, ByteBuffer.allocate(512));
        byteBuffers.put(128, ByteBuffer.allocate(128));
        byteBuffers.put(32, ByteBuffer.allocate(32));
        byteBuffers.put(8, ByteBuffer.allocate(8));
        byteBuffers.put(2, ByteBuffer.allocate(2));
        byteBuffers.put(1, ByteBuffer.allocate(1));
    }

    public void doCollect() throws IOException {
        ServerSocketChannel serverSocketChannel = initServerSocketChannel();
        ByteBuffer contextLengthBuffer = ByteBuffer.allocate(4);
        while (selector.select() > 0) {
            Iterator<?> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = (SelectionKey) iterator.next();
                iterator.remove();
                beginToRead(serverSocketChannel, key);
                if (key.isReadable()) {
                    ByteChannel sc = (SocketChannel) key.channel();
                    try {
                        sc.read(contextLengthBuffer);
                        int length = ByteArrayUtil.byteArrayToInt(contextLengthBuffer.array(), 0);
                        if (length > 0) {
                            readDataFromSocketChannel(length, byteBuffers, sc);
                        }
                    } catch (IOException e) {
                        logger.error("The remote client disconnect service", e);
                        sc.close();
                    } finally {
                        contextLengthBuffer.clear();
                    }
                }
            }
        }
    }

    public static void readDataFromSocketChannel(int length, Map<Integer, ByteBuffer> byteBuffers, ByteChannel byteChannel) {
        int tmp = length;
        byte[] tmpBytes = new byte[length];
        int tmpLength = 0;
        for (Map.Entry<Integer, ByteBuffer> entry : byteBuffers.entrySet()) {
            int j = tmp / entry.getKey();
            if (j == 0) {
                continue;
            }
            for (int k = 0; k < j; k++) {
                try {
                    byteChannel.read(entry.getValue());
                    System.arraycopy(entry.getValue().array(), 0, tmpBytes, tmpLength, entry.getValue().array().length);
                } catch (IOException e) {
                    logger.error("Read data From socket channel", e);
                } finally {
                    entry.getValue().clear();
                }
                tmpLength += entry.getKey();
            }
            tmp = tmp % entry.getKey();
        }
        DataBufferThreadContainer.getDataBufferThread().doCarry(tmpBytes);
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
