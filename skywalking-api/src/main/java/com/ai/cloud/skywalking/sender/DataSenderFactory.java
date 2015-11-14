package com.ai.cloud.skywalking.sender;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.util.StringUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.ai.cloud.skywalking.conf.Config.Sender.CONNECT_PERCENT;
import static com.ai.cloud.skywalking.conf.Config.Sender.RETRY_GET_SENDER_WAIT_INTERVAL;
import static com.ai.cloud.skywalking.conf.Config.SenderChecker.CHECK_POLLING_TIME;

public class DataSenderFactory {

    private static Logger logger = Logger.getLogger(DataSenderFactory.getSender().toString());

    private static Set<SocketAddress> socketAddresses = new HashSet<SocketAddress>();
    private static Set<SocketAddress> unUsedSocketAddresses = new HashSet<SocketAddress>();
    private static List<DataSender> availableSenders = new ArrayList<DataSender>();
    private static DataSenderChecker dataSenderChecker;
    private static Object lock = new Object();

    static {
        try {
            if (StringUtil.isEmpty(Config.Sender.SERVERS_ADDR)) {
                throw new IllegalArgumentException("Collection service configuration error.");
            }

            for (String serverConfig : Config.Sender.SERVERS_ADDR.split(";")) {
                String[] server = serverConfig.split(":");
                if (server.length != 2)
                    throw new IllegalArgumentException("Collection service configuration error.");
                socketAddresses.add(new InetSocketAddress(server[0], Integer.valueOf(server[1])));
            }
        } catch (Exception e) {
            logger.log(Level.ALL, "Collection service configuration error.");
            System.exit(-1);
        }

        dataSenderChecker = new DataSenderChecker();
        dataSenderChecker.start();
    }

    public static DataSender getSender() {
        while (availableSenders.size() == 0) {
            try {
                Thread.sleep(RETRY_GET_SENDER_WAIT_INTERVAL);
            } catch (InterruptedException e) {
                logger.log(Level.ALL, "Sleep failure");
            }
        }
        return availableSenders.get(ThreadLocalRandom.current().nextInt(0, availableSenders.size()));
    }

    static class DataSenderChecker extends Thread {

        private int availableSize;

        public DataSenderChecker() {
            if (CONNECT_PERCENT <= 0 || CONNECT_PERCENT > 100) {
                logger.log(Level.ALL, "CONNECT_PERCENT must between 1 and 100");
                System.exit(-1);
            }
            availableSize = (int) Math.ceil(socketAddresses.size() * ((1.0 * CONNECT_PERCENT / 100) % 100));
            // 初始化DataSender
            List<SocketAddress> usedSocketAddress = new ArrayList<SocketAddress>();

            for (SocketAddress socketAddress : socketAddresses) {
                if (availableSenders.size() >= availableSize) {
                    break;
                }
                try {
                    availableSenders.add(new DataSender(socketAddress));
                    usedSocketAddress.add(socketAddress);
                } catch (IOException e) {
                    unUsedSocketAddresses.add(socketAddress);
                }
            }
            unUsedSocketAddresses = new HashSet<SocketAddress>(socketAddresses);
            unUsedSocketAddresses.removeAll(usedSocketAddress);
        }

        public void run() {
            Iterator<SocketAddress> unUsedSocketAddressIterator;
            SocketAddress tmpScoketAddress;
            while (true) {
                unUsedSocketAddressIterator = unUsedSocketAddresses.iterator();
                while (unUsedSocketAddressIterator.hasNext()) {

                    tmpScoketAddress = unUsedSocketAddressIterator.next();
                    if (availableSenders.size() >= availableSize) {
                        break;
                    }

                    try {
                        availableSenders.add(new DataSender(tmpScoketAddress));
                        unUsedSocketAddresses.remove(tmpScoketAddress);
                    } catch (IOException e) {

                    }
                }

                try {
                    Thread.sleep(CHECK_POLLING_TIME);
                } catch (InterruptedException e) {
                    logger.log(Level.ALL, "Sleep Failure");
                }
            }
        }
    }

    public static void unRegister(DataSender sender) {
        synchronized (lock) {
            availableSenders.remove(sender);
            unUsedSocketAddresses.add(sender.getServerIp());
        }
    }

}
