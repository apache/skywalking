package com.ai.cloud.skywalking.sender;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


public class DataSenderFactory {

    private static List<SocketAddress> socketAddresses = new ArrayList<SocketAddress>();
    private static List<SocketAddress> unUsedSocketAddresses = new ArrayList<SocketAddress>();
    private static List<DataSender> availableSenders = new ArrayList<DataSender>();

    static {
        socketAddresses.add(new InetSocketAddress("10.1.235.197", 34000));
        socketAddresses.add(new InetSocketAddress("10.1.235.197", 35000));
        new DataSenderMaker().start();
    }

    public static DataSender getSender() {
        return availableSenders.get(ThreadLocalRandom.current().nextInt(availableSenders.size()));
    }

    static class DataSenderMaker extends Thread {

        public DataSenderMaker() {
            // 初始化DataSender
            Iterator<SocketAddress> it = socketAddresses.iterator();
            List<SocketAddress> usedSocketAddress = new ArrayList<SocketAddress>();
            for (SocketAddress socketAddress : socketAddresses) {
                if (availableSenders.size() >= socketAddresses.size() / 2) {
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
                //当可用的Sender的数量和保存的地址的比例不在1:2,则不创建
                for (SocketAddress socketAddress : unUsedSocketAddresses) {
                    if (availableSenders.size() >= socketAddresses.size() / 2) {
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
