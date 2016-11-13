package com.a.eye.skywalking.storage.util;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.regex.Pattern;

/**
 * Created by xin on 2016/11/12.
 */
public class NetUtils {

    private static       ILog    logger     = LogManager.getLogger(NetUtils.class);
    public static final  String  LOCALHOST  = "127.0.0.1";
    public static final  String  ANYHOST    = "0.0.0.0";
    private static final Pattern IP_PATTERN = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3,5}$");

    public static InetAddress getLocalAddress() {
        InetAddress localAddress = null;
        try {
            localAddress = InetAddress.getLocalHost();
            if (isValidAddress(localAddress)) {
                return localAddress;
            }
        } catch (Throwable e) {
            logger.warn("Failed to get ip address.", e);
        }

        try {
            // 获取所有的网卡
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    try {
                        NetworkInterface network = interfaces.nextElement();
                        // 遍历网卡中所有绑定的地址
                        Enumeration<InetAddress> addresses = network.getInetAddresses();
                        if (addresses != null) {
                            while (addresses.hasMoreElements()) {
                                try {
                                    InetAddress address = addresses.nextElement();
                                    // 判断地址是否为合法的IP地址
                                    if (isValidAddress(address)) {
                                        return address;
                                    }
                                } catch (Throwable e) {
                                    logger.warn("Failed to get ip address.", e);
                                }
                            }
                        }
                    } catch (Throwable e) {
                        logger.warn("Failed to get ip address.", e);
                    }
                }
            }
        } catch (Throwable e) {
            logger.warn("Failed to get ip address.", e);
        }

        return localAddress;
    }

    private static boolean isValidAddress(InetAddress address) {
        if (address == null || address.isLoopbackAddress())
            return false;
        String name = address.getHostAddress();
        // 不能是0.0.0.0 也不能是127.0.0.1 并且还得符合IP的正则
        return (name != null && !ANYHOST.equals(name) && !LOCALHOST.equals(name) && IP_PATTERN.matcher(name).matches());
    }

}
