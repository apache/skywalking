package org.skywalking.apm.agent.core.os;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.network.proto.OSInfo;

/**
 * @author wusheng
 */
public class OSUtil {
    private static volatile String OS_NAME;
    private static volatile String HOST_NAME;
    private static volatile List<String> IPV4_LIST;

    public static String getOsName() {
        if (OS_NAME == null) {
            OS_NAME = System.getProperty("os.name");
        }
        return OS_NAME;
    }

    public static String getHostName() {
        if (HOST_NAME == null) {
            try {
                InetAddress host = InetAddress.getLocalHost();
                HOST_NAME = host.getHostName();
            } catch (UnknownHostException e) {
                HOST_NAME = "unknown";
            }
        }
        return HOST_NAME;
    }

    public static List<String> getAllIPV4() {
        if (IPV4_LIST == null) {
            IPV4_LIST = new LinkedList<String>();
            try {
                Enumeration<NetworkInterface> interfs = NetworkInterface.getNetworkInterfaces();
                while (interfs.hasMoreElements()) {
                    NetworkInterface networkInterface = interfs.nextElement();
                    Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress address = inetAddresses.nextElement();
                        if (address instanceof Inet4Address) {
                            String addressStr = address.getHostAddress();
                            if ("127.0.0.1".equals(addressStr)) {
                                continue;
                            }
                            IPV4_LIST.add(addressStr);
                        }
                    }
                }
            } catch (SocketException e) {

            }
        }
        return IPV4_LIST;
    }

    public static OSInfo buildOSInfo() {
        OSInfo.Builder builder = OSInfo.newBuilder();
        String osName = getOsName();
        if (osName != null) {
            builder.setOsName(osName);
        }
        String hostName = getHostName();
        if (hostName != null) {
            builder.setHostname(hostName);
        }
        List<String> allIPV4 = getAllIPV4();
        if (allIPV4.size() > 0) {
            builder.addAllIpv4S(allIPV4);
        }
        return builder.build();
    }
}
