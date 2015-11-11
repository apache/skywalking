package com.ai.cloud.skywalking.sender;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.util.StringUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.ai.cloud.skywalking.conf.Config.Sender.SEND_CONNECTION_THRESHOLD;

public class DataSenderFactory {

    private static List<SocketAddress> socketAddresses = new ArrayList<SocketAddress>();
    private static List<SocketAddress> unUsedSocketAddresses = new ArrayList<SocketAddress>();
    private static List<DataSender> availableSenders = new ArrayList<DataSender>();

    static {
        try {
            if (StringUtil.isEmpty(Config.Sender.SENDER_SERVERS)) {
                throw new IllegalArgumentException("Collection service configuration error.");
            }

            for (String serverConfig : Config.Sender.SENDER_SERVERS.split(";")) {
                String[] server = serverConfig.split(":");
                if (server.length != 2)
                    throw new IllegalArgumentException("Collection service configuration error.");
                socketAddresses.add(new InetSocketAddress(server[0], Integer.valueOf(server[1])));
            }
        } catch (Exception e) {
            System.err.print("Collection service configuration error.");
            System.exit(-1);
        }

        new DataSenderMaker().start();
    }

    public static DataSender getSender() {
        while(availableSenders.size() <= 0){
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return availableSenders.get(ThreadLocalRandom.current().nextInt(0, availableSenders.size()));
    }

    static class DataSenderMaker extends Thread {

        private int avaiableSize = (int) Math.ceil(socketAddresses.size() * 1.0 / SEND_CONNECTION_THRESHOLD);

        public DataSenderMaker() {
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
            unUsedSocketAddresses = new ArrayList<SocketAddress>(socketAddresses);
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
                    Thread.sleep(3000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    public static void unRegister(DataSender sender) {
        availableSenders.remove(sender);
        unUsedSocketAddresses.add(sender.getServerIp());
    }

}
