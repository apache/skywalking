package com.ai.cloud.skywalking.sender;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.util.StringUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.ai.cloud.skywalking.conf.Config.Sender.*;

public class DataSenderFactoryWithBalance {

    private static Logger logger = Logger.getLogger(DataSenderFactoryWithBalance.class.getName());
    // unUsedServerAddress存放没有使用的服务器地址，
    private static List<InetSocketAddress> unusedServerAddresses = new ArrayList<InetSocketAddress>();

    private static List<DataSender> usingDataSender = new ArrayList<DataSender>();
    private static int maxKeepConnectingSenderSize;
    private static Object lock = new Object();
    private static boolean NEED_ADD_SENDER_FLAG = false;

    private static int calculateMaxKeeperConnectingSenderSize(int allAddressSize) {
        if (CONNECT_PERCENT <= 0 || CONNECT_PERCENT > 100) {
            logger.log(Level.ALL, "CONNECT_PERCENT must between 1 and 100");
            System.exit(-1);
        }
        return (int) Math.ceil(allAddressSize * ((1.0 * CONNECT_PERCENT / 100) % 100));
    }

    // 初始化服务端的地址数据
    static {
        // 获取数据
        if (StringUtil.isEmpty(Config.Sender.SERVERS_ADDR)) {
            throw new IllegalArgumentException("Collection service configuration error.");
        }

        // 初始化地址
        Set<InetSocketAddress> tmpInetSocketAddress = new HashSet<InetSocketAddress>();
        for (String serverConfig : Config.Sender.SERVERS_ADDR.split(";")) {
            String[] server = serverConfig.split(":");
            if (server.length != 2)
                throw new IllegalArgumentException("Collection service configuration error.");
            tmpInetSocketAddress.add(new InetSocketAddress(server[0], Integer.valueOf(server[1])));
        }

        unusedServerAddresses.addAll(tmpInetSocketAddress);

        //根据配置的服务器集群的地址，来计算保持连接的Sender的数量
        maxKeepConnectingSenderSize = calculateMaxKeeperConnectingSenderSize(tmpInetSocketAddress.size());


        // 初始化的发送程序
        int index = 0;
        while (usingDataSender.size() < maxKeepConnectingSenderSize) {
            index = ThreadLocalRandom.current().nextInt(0, unusedServerAddresses.size());
            try {
                usingDataSender.add(new DataSender(unusedServerAddresses.get(index)));
                unusedServerAddresses.remove(index);
            } catch (IOException e) {
                // 服务器连接不上
                logger.log(Level.SEVERE, "Failed to connect server[" +
                        unusedServerAddresses.get(index).getHostName() + "]");
                continue;
            }
        }

        new DataSenderChecker().start();
    }

    // 获取连接

    public static DataSender getSender() {
        DataSender readySender = null;
        while (true) {
            int index = ThreadLocalRandom.current().nextInt(0, usingDataSender.size());
            if (usingDataSender.get(index).getStatus() == DataSender.SenderStatus.READY) {
                readySender = usingDataSender.get(index);
                break;
            }

            if (readySender == null) {
                try {
                    Thread.sleep(RETRY_GET_SENDER_WAIT_INTERVAL);
                } catch (InterruptedException e) {
                    logger.log(Level.ALL, "Sleep failed");
                }
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
                // 检查是否需要新增
                // NEED_ADD_SENDER_FLAG 将会在unRegister方法修改值
                if (NEED_ADD_SENDER_FLAG) {
                    DataSender newSender;
                    for (int i = 0; i < usingDataSender.size(); i++) {
                        if (usingDataSender.get(i).getStatus() == DataSender.SenderStatus.FAILED) {
                            // 正在使用的Sender的数量 <= maxKeepConnectingSenderSize
                            // 剩余的服务器地址数量 = 总得服务器地址数量  - 正在使用的Sender的数量
                            // 可替换的服务器数量 = 剩余服务器地址数量
                            // 当剩余服务器地址数量 <= 0 时，可以替换的地址也不存在，替换操作就可以不执行，所以这里的while是这样的意思
                            // 当剩余服务器地址数量 > 0 时， 就可以找到可以替换的地址，替换操作也就可以执行了，这里的就会跳出while循环
                            while ((newSender = findReadySender()) == null) {
                                try {
                                    Thread.sleep(RETRY_FIND_CONNECTION_SENDER);
                                } catch (InterruptedException e) {
                                    logger.log(Level.ALL, "Sleep failed.");
                                }
                            }
                            // 找到可以替换的Sender
                            usingDataSender.set(i, newSender);
                            unusedServerAddresses.add(usingDataSender.get(i).getServerIp());
                            if (usingDataSender.size() >= maxKeepConnectingSenderSize) {
                                NEED_ADD_SENDER_FLAG = false;
                                break;
                            }
                        }
                    }


                }

                // 检查是否需要替换
                if (sleepTime >= SWITCH_SENDER_INTERVAL) {
                    DataSender toBeSwitchSender;
                    DataSender tmpSender;
                    while (true) {
                        int toBeSwitchIndex = ThreadLocalRandom.current().nextInt(0, usingDataSender.size() - 1);
                        toBeSwitchSender = usingDataSender.get(toBeSwitchIndex);
                        if (toBeSwitchSender.getStatus() == DataSender.SenderStatus.READY) {
                            tmpSender = findReadySender();
                            // 找到可以替换的Sender
                            if (tmpSender != null) {
                                usingDataSender.set(toBeSwitchIndex, tmpSender);
                                try {
                                    Thread.sleep(CLOSE_SENDER_COUNTDOWN);
                                } catch (InterruptedException e) {
                                    logger.log(Level.ALL, "Sleep Failed");
                                }
                                unusedServerAddresses.remove(tmpSender.getServerIp());
                                unusedServerAddresses.add(toBeSwitchSender.getServerIp());
                            }
                            break;
                        }
                    }
                    sleepTime = 0;
                }

                //
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
        for (InetSocketAddress serverAddress : unusedServerAddresses) {
            try {
                result = new DataSender(serverAddress);
                break;
            } catch (IOException e) {
                if (result != null) {
                    try {
                        result.closeConnect();
                    } catch (IOException ex) {
                        logger.log(Level.ALL, "Failed to close socket[" +
                                serverAddress.getHostName() + "]");
                    }
                }
                continue;
            }
        }
        return result;
    }

    public static void unRegister(DataSender socket) {
        int index = usingDataSender.indexOf(socket);
        if (index != -1) {
            usingDataSender.get(index).setStatus(DataSender.SenderStatus.FAILED);
        }
        NEED_ADD_SENDER_FLAG = true;
    }
}
