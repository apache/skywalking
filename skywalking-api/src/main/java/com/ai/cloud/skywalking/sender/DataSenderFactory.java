package com.ai.cloud.skywalking.sender;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.util.StringUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.ai.cloud.skywalking.conf.Config.Sender.CONNECT_PERCENT;

public class DataSenderFactory {

    private static Set<SocketAddress> socketAddresses = new HashSet<SocketAddress>();
    private static Set<SocketAddress> unUsedSocketAddresses = new HashSet<SocketAddress>();
    private static List<DataSender> availableSenders = new ArrayList<DataSender>();
    private static DataSenderMaker dataSenderMaker;
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
            System.err.print("Collection service configuration error.");
            System.exit(-1);
        }

        dataSenderMaker = new DataSenderMaker();
        dataSenderMaker.start();
    }

    public static DataSender getSender() {
        while (availableSenders.size() == 0) {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return availableSenders.get(ThreadLocalRandom.current().nextInt(0, availableSenders.size()));
    }

    static class DataSenderMaker extends Thread {

        private int avaiableSize;

        public DataSenderMaker() {
            if (CONNECT_PERCENT <= 0 || CONNECT_PERCENT > 100) {
                System.err.println("CONNECT_PERCENT must between 1 and 100");
                System.exit(-1);
            }
            avaiableSize = (int) Math.ceil(socketAddresses.size() * ((1.0 * CONNECT_PERCENT / 100) % 100));
            // 初始化DataSender
            Iterator<SocketAddress> it = socketAddresses.iterator();
            List<SocketAddress> usedSocketAddress = new ArrayList<SocketAddress>();

            for (SocketAddress socketAddress : socketAddresses) {
                if (availableSenders.size() >= avaiableSize) {
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
            while (true) {
                for (SocketAddress socketAddress : unUsedSocketAddresses) {
                    if (availableSenders.size() >= avaiableSize) {
                        break;
                    }
                    try {
                        availableSenders.add(new DataSender(socketAddress));
                    } catch (IOException e) {
                        // 当前发送的地址还是不可用
                    }
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
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
