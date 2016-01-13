package com.ai.cloud.skywalking.sender;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.util.StringUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.ai.cloud.skywalking.conf.Config.Sender.*;

public class DataSenderFactoryWithBalance {

    private static Logger logger = Logger
            .getLogger(DataSenderFactoryWithBalance.class.getName());
    // unUsedServerAddress存放没有使用的服务器地址，
    private static List<InetSocketAddress> unusedServerAddresses = new ArrayList<InetSocketAddress>();

    private static List<DataSender> usingDataSender = new ArrayList<DataSender>();
    private static int maxKeepConnectingSenderSize;

    private static int calculateMaxKeeperConnectingSenderSize(int allAddressSize) {
        if (CONNECT_PERCENT <= 0 || CONNECT_PERCENT > 100) {
            logger.log(Level.ALL, "CONNECT_PERCENT must between 1 and 100");
            System.exit(-1);
        }
        return (int) Math.ceil(allAddressSize
                * ((1.0 * CONNECT_PERCENT / 100) % 100));
    }

    // 初始化服务端的地址数据
    static {
        // 获取数据
        if (StringUtil.isEmpty(Config.Sender.SERVERS_ADDR)) {
            throw new IllegalArgumentException(
                    "Collection service configuration error.");
        }

        // 初始化地址
        Set<InetSocketAddress> tmpInetSocketAddress = new HashSet<InetSocketAddress>();
        for (String serverConfig : Config.Sender.SERVERS_ADDR.split(";")) {
            String[] server = serverConfig.split(":");
            if (server.length != 2)
                throw new IllegalArgumentException(
                        "Collection service configuration error.");
            tmpInetSocketAddress.add(new InetSocketAddress(server[0], Integer
                    .valueOf(server[1])));
        }

        unusedServerAddresses.addAll(tmpInetSocketAddress);

        // 根据配置的服务器集群的地址，来计算保持连接的Sender的数量
        maxKeepConnectingSenderSize = calculateMaxKeeperConnectingSenderSize(tmpInetSocketAddress
                .size());
        // 最大连接消费线程小于保持连接的Sender的数量，就不需要保持那么多的保持连接的Sender的数量
        if (maxKeepConnectingSenderSize > Config.Consumer.MAX_CONSUMER
                * Config.Buffer.POOL_SIZE) {
            maxKeepConnectingSenderSize = Config.Consumer.MAX_CONSUMER
                    * Config.Buffer.POOL_SIZE;
        }

        new DataSenderChecker().start();
    }

    // 获取连接
    public static IDataSender getSender() {
        DataSenderWithCopies readySender = new DataSenderWithCopies(maxKeepConnectingSenderSize);
        while (true) {
            try {
                if (usingDataSender.size() > 0) {
                    int index = ThreadLocalRandom.current().nextInt(0,
                            usingDataSender.size());
                    if (usingDataSender.get(index).getStatus() == DataSender.SenderStatus.READY) {
                        while (readySender.append(usingDataSender.get(index))) {
                            if (++index == usingDataSender.size()) {
                                index = 0;
                            }
                        }
                        break;
                    }
                }

                if (!readySender.isReady()) {
                    try {
                        Thread.sleep(RETRY_GET_SENDER_WAIT_INTERVAL);
                    } catch (InterruptedException e) {
                        logger.log(Level.ALL, "Sleep failed", e);
                    }
                }
            } catch (Throwable e) {
                logger.log(Level.ALL, "get sender failed", e);
            }

        }

        return readySender;
    }

    // 定时Sender状态检查
    public static class DataSenderChecker extends Thread {
        public DataSenderChecker() {
            super("Data-Sender-Checker");
        }

        @Override
        public void run() {
            long sleepTime = 0;
            while (true) {
                try {
                    DataSender newSender;
                    // removing failed sender
                    Iterator<DataSender> senderIterator = usingDataSender
                            .iterator();
                    DataSender tmpDataSender;
                    while (senderIterator.hasNext()) {
                        tmpDataSender = senderIterator.next();
                        if (tmpDataSender.getStatus() == DataSender.SenderStatus.FAILED) {
                            tmpDataSender.close();
                            unusedServerAddresses.add(tmpDataSender
                                    .getServerIp());
                            senderIterator.remove();
                        }
                    }

                    // try to fill up senders. if size is not enough.
                    while (usingDataSender.size() < maxKeepConnectingSenderSize) {
                        if ((newSender = findReadySender()) == null) {
                            // no available sender. ignore.
                            break;
                        }
                        usingDataSender.add(newSender);

                    }

                    // try to switch.
                    if (sleepTime >= SWITCH_SENDER_INTERVAL) {
                        // if sender is enough, go to switch for balancing.
                        if (usingDataSender.size() >= maxKeepConnectingSenderSize) {
                            DataSender toBeSwitchSender;
                            DataSender tmpSender;

                            int toBeSwitchIndex;

                            if (usingDataSender.size() - 1 > 0) {
                                toBeSwitchIndex = ThreadLocalRandom.current()
                                        .nextInt(0, usingDataSender.size() - 1);
                            } else {
                                toBeSwitchIndex = 0;
                            }

                            toBeSwitchSender = usingDataSender
                                    .get(toBeSwitchIndex);
                            tmpSender = findReadySender();
                            if (tmpSender != null) {
                                usingDataSender.set(toBeSwitchIndex, tmpSender);
                                try {
                                    Thread.sleep(CLOSE_SENDER_COUNTDOWN);
                                } catch (InterruptedException e) {
                                    logger.log(Level.ALL, "Sleep Failed");
                                }
                                toBeSwitchSender.close();
                                unusedServerAddresses.remove(tmpSender
                                        .getServerIp());
                                unusedServerAddresses.add(toBeSwitchSender
                                        .getServerIp());
                            }
                        }
                        sleepTime = 0;
                    }
                } catch (Throwable e) {
                    logger.log(Level.ALL, "DataSenderChecker running failed", e);
                }

                sleepTime += CHECKER_THREAD_WAIT_INTERVAL;
                try {
                    Thread.sleep(CHECKER_THREAD_WAIT_INTERVAL);
                } catch (InterruptedException e) {
                    logger.log(Level.ALL, "Sleep failed");
                }

            }
        }
    }

    private static DataSender findReadySender() {

        DataSender result = null;
        int index = ThreadLocalRandom.current().nextInt(0,
                unusedServerAddresses.size());
        for (int i = 0; i < unusedServerAddresses.size();i++, index++) {

            if(index == unusedServerAddresses.size()){
                index = 0;
            }

            try {
                result = new DataSender(unusedServerAddresses.get(index));
                unusedServerAddresses.remove(index);
                break;
            } catch (IOException e) {
                if (result != null) {
                    result.close();
                }
                continue;
            }
        }

        return result;
    }

    public static void unRegister(DataSender socket) {
        int index = usingDataSender.indexOf(socket);
        if (index != -1) {
            usingDataSender.get(index)
                    .setStatus(DataSender.SenderStatus.FAILED);
        }
    }
}
