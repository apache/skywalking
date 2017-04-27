package org.skywalking.apm.api.util;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class MachineInfo {
    private static int PROCESS_NO = -1;
    private static String IP;
    private static String HOST_NAME;

    static {
        PROCESS_NO = getProcessNo();
    }

    public static int getProcessNo() {
        if (PROCESS_NO == -1) {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            try {
                PROCESS_NO = Integer.parseInt(name.split("@")[0]);
            } catch (Throwable t) {
                PROCESS_NO = 0;
            }
        }
        return PROCESS_NO;
    }

    private static InetAddress getInetAddress() {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            HOST_NAME = "unknown host!";
        }
        return null;

    }

    public static String getHostIp() {
        if (StringUtil.isEmpty(IP)) {
            InetAddress netAddress = getInetAddress();
            if (null == netAddress) {
                IP = "N/A";
            } else {
                IP = netAddress.getHostAddress(); //get the ip address
            }
        }
        return IP;
    }

    public static String getHostName() {
        if (StringUtil.isEmpty(HOST_NAME)) {
            InetAddress netAddress = getInetAddress();
            if (null == netAddress) {
                HOST_NAME = "N/A";
            } else {
                HOST_NAME = netAddress.getHostName(); //get the host address
            }
        }
        return HOST_NAME;
    }

    public static String getHostDesc() {
        return getHostName() + "/" + getHostIp();
    }

    private MachineInfo() {
        // Non
    }

}
