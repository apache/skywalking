package com.a.eye.skywalking.sender;

import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.Logger;
import com.a.eye.skywalking.selfexamination.HeathReading;
import com.a.eye.skywalking.selfexamination.SDKHealthCollector;
import com.a.eye.skywalking.protocol.util.StringUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DataSenderFactoryWithBalance {

    private static Logger                  logger                = LogManager.getLogger(DataSenderFactoryWithBalance.class);
    // unUsedServerAddress存放没有使用的服务器地址，
    private static List<InetSocketAddress> unusedServerAddresses = new ArrayList<InetSocketAddress>();

    private static List<DataSender> usingDataSender = new ArrayList<DataSender>();
    private static int maxKeepConnectingSenderSize;

    private static int calculateMaxKeeperConnectingSenderSize(int allAddressSize) {
        if (Config.Sender.CONNECT_PERCENT <= 0 || Config.Sender.CONNECT_PERCENT > 100) {
            logger.error("CONNECT_PERCENT must between 1 and 100");
            System.exit(-1);
        }
        return (int) Math.ceil(allAddressSize
                * ((1.0 * Config.Sender.CONNECT_PERCENT / 100) % 100));
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
                        Thread.sleep(Config.Sender.RETRY_GET_SENDER_WAIT_INTERVAL);
                    } catch (InterruptedException e) {
                        logger.error("Sleep failed", e);
                    }
                }
            } catch (Throwable e) {
                logger.error("get sender failed", e);
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
                                    .getServerAddr());
                            senderIterator.remove();
                            SDKHealthCollector.getCurrentHeathReading("remove").updateData(HeathReading.INFO, "remove disconnected sender.");
                        }
                    }

                    // try to fill up senders. if size is not enough.
                    while (unusedServerAddresses.size() > 0 && usingDataSender.size() < maxKeepConnectingSenderSize) {
                        if ((newSender = findReadySender()) == null) {
                            // no available sender. ignore.
                            break;
                        }
                        usingDataSender.add(newSender);
                        SDKHealthCollector.getCurrentHeathReading("add").updateData(HeathReading.INFO, "add new sender.");
                    }

                    // try to switch.
                    if (sleepTime >= Config.Sender.SWITCH_SENDER_INTERVAL && unusedServerAddresses.size() > 0) {
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
                                    Thread.sleep(Config.Sender.CLOSE_SENDER_COUNTDOWN);
                                } catch (InterruptedException e) {
                                    logger.error("Sleep Failed", e);
                                }
                                toBeSwitchSender.close();
                                unusedServerAddresses.remove(tmpSender
                                        .getServerAddr());
                                unusedServerAddresses.add(toBeSwitchSender
                                        .getServerAddr());
                                SDKHealthCollector.getCurrentHeathReading("switch").updateData(HeathReading.INFO, "switch existed sender.");
                            }
                        }
                        sleepTime = 0;
                    }
                } catch (Throwable e) {
                    SDKHealthCollector.getCurrentHeathReading(null).updateData(HeathReading.ERROR, "DataSenderChecker running failed:" + e.getMessage());
                    logger.error("DataSenderChecker running failed", e);
                } finally {
                    SDKHealthCollector.getCurrentHeathReading(null).updateData(HeathReading.INFO, "using available DataSender connect to: " + listUsingServers());
                }

                sleepTime += Config.Sender.CHECKER_THREAD_WAIT_INTERVAL;
                try {
                    Thread.sleep(Config.Sender.CHECKER_THREAD_WAIT_INTERVAL);
                } catch (InterruptedException e) {
                    logger.error("Sleep failed");
                }

            }
        }
    }

    private static DataSender findReadySender() {
        DataSender result = null;
        int index = 0;

        if (unusedServerAddresses.size() > 1) {
            index = ThreadLocalRandom.current().nextInt(0,
                    unusedServerAddresses.size());
        }

        for (int i = 0; i < unusedServerAddresses.size(); i++, index++) {

            if (index == unusedServerAddresses.size()) {
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
        socket.setStatus(DataSender.SenderStatus.FAILED);
    }

    private static String listUsingServers() {
        StringBuilder usingAddrDesc = new StringBuilder();
        if (usingDataSender.size() > 0) {
            for (DataSender sender : usingDataSender) {
                if (usingAddrDesc.length() > 0) {
                    usingAddrDesc.append(",");
                }
                usingAddrDesc.append(sender.getServerAddr().toString());
            }
        }
        return usingAddrDesc.toString();
    }
}
