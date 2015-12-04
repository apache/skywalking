package com.ai.cloud.skywalking.sender;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.selfexamination.HealthCollector;
import com.ai.cloud.skywalking.selfexamination.HeathReading;
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

    private static Logger logger = Logger.getLogger(DataSenderFactory.class.getName());

    private static List<SocketAddress> socketAddresses = new ArrayList<SocketAddress>();
    private static List<SocketAddress> unUsedSocketAddresses = new ArrayList<SocketAddress>();
    private static List<DataSender> availableSenders = new ArrayList<DataSender>();
    private static Object lock = new Object();

    static {
        try {
            if (StringUtil.isEmpty(Config.Sender.SERVERS_ADDR)) {
                throw new IllegalArgumentException("Collection service configuration error.");
            }
            //过滤重复地址
            Set<SocketAddress> tmpSocktAddress = new HashSet<SocketAddress>();
            for (String serverConfig : Config.Sender.SERVERS_ADDR.split(";")) {
                String[] server = serverConfig.split(":");
                if (server.length != 2)
                    throw new IllegalArgumentException("Collection service configuration error.");
                tmpSocktAddress.add(new InetSocketAddress(server[0], Integer.valueOf(server[1])));
            }

            socketAddresses.addAll(tmpSocktAddress);

        } catch (Exception e) {
            logger.log(Level.ALL, "Collection service configuration error.", e);
            System.exit(-1);
        }

        new DataSenderChecker().start();
    }

    public static DataSender getSender() {
        while (availableSenders.size() == 0) {
            try {
                Thread.sleep(RETRY_GET_SENDER_WAIT_INTERVAL);
            } catch (InterruptedException e) {
                logger.log(Level.ALL, "Sleep failure", e);
            }
        }
        return availableSenders.get(ThreadLocalRandom.current().nextInt(0, availableSenders.size()));
    }

    static class DataSenderChecker extends Thread {

        private int availableSize;

        public DataSenderChecker() {
            super("DataSenderChecker");

            if (CONNECT_PERCENT <= 0 || CONNECT_PERCENT > 100) {
                logger.log(Level.ALL, "CONNECT_PERCENT must between 1 and 100");
                System.exit(-1);
            }
            availableSize = (int) Math.ceil(socketAddresses.size() * ((1.0 * CONNECT_PERCENT / 100) % 100));
            // 初始化DataSender
            List<SocketAddress> usedSocketAddress = new ArrayList<SocketAddress>();

            int index;
            while (availableSenders.size() < availableSize) {
                // 随机获取服务器地址
                index = ThreadLocalRandom.current().nextInt(socketAddresses.size());
                try {
                    availableSenders.add(new DataSender(socketAddresses.get(index)));
                    usedSocketAddress.add(socketAddresses.get(index));
                } catch (IOException e) {
                    unUsedSocketAddresses.add(socketAddresses.get(index));
                }
            }
            unUsedSocketAddresses = new ArrayList<SocketAddress>(socketAddresses);
            unUsedSocketAddresses.removeAll(usedSocketAddress);
        }

        public void run() {
            Iterator<SocketAddress> unUsedSocketAddressIterator;
            SocketAddress tmpSocketAddress;
            while (true) {
                unUsedSocketAddressIterator = unUsedSocketAddresses.iterator();
                while (unUsedSocketAddressIterator.hasNext()) {

                    tmpSocketAddress = unUsedSocketAddressIterator.next();
                    if (availableSenders.size() >= availableSize) {
                        HealthCollector.getCurrentHeathReading(null).updateData(HeathReading.INFO, "the num of available senders is enough.");
                        break;
                    }

                    synchronized (lock) {
                        try {
                            HealthCollector.getCurrentHeathReading(null).updateData(HeathReading.INFO, "increasing available senders.");
                            availableSenders.add(new DataSender(tmpSocketAddress));
                            unUsedSocketAddresses.remove(tmpSocketAddress);
                        } catch (IOException e) {

                        }
                    }
                }

                if (availableSenders.size() >= availableSize) {
                    HealthCollector.getCurrentHeathReading(null).updateData(HeathReading.WARNING, "the num of available senders is not enough (" + availableSenders.size() + ").");
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
