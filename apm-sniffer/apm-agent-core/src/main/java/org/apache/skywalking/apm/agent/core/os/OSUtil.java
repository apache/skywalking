/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.agent.core.os;

import java.lang.management.ManagementFactory;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;

public class OSUtil {
    private static volatile String OS_NAME;
    private static volatile String HOST_NAME;
    private static volatile List<String> IPV4_LIST;
    private static volatile int PROCESS_NO = 0;

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
            IPV4_LIST = new LinkedList<>();
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
                            } else if ("localhost".equals(addressStr)) {
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

    public static String getIPV4() {
        final List<String> allIPV4 = getAllIPV4();
        if (allIPV4.size() > 0) {
            return allIPV4.get(0);
        } else {
            return "no-hostname";
        }
    }

    public static int getProcessNo() {
        if (PROCESS_NO == 0) {
            try {
                PROCESS_NO = Integer.parseInt(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
            } catch (Exception e) {
                PROCESS_NO = -1;
            }
        }
        return PROCESS_NO;
    }

    public static List<KeyStringValuePair> buildOSInfo(int ipv4Size) {
        List<KeyStringValuePair> osInfo = new ArrayList<>();

        String osName = getOsName();
        if (osName != null) {
            osInfo.add(KeyStringValuePair.newBuilder().setKey("OS Name").setValue(osName).build());
        }
        String hostName = getHostName();
        if (hostName != null) {
            osInfo.add(KeyStringValuePair.newBuilder().setKey("hostname").setValue(hostName).build());
        }
        List<String> allIPV4 = getAllIPV4();
        if (allIPV4.size() > 0) {
            if (allIPV4.size() > ipv4Size) {
                allIPV4 = allIPV4.subList(0, ipv4Size);
            }
            for (String ipv4 : allIPV4) {
                osInfo.add(KeyStringValuePair.newBuilder().setKey("ipv4").setValue(ipv4).build());
            }
        }
        osInfo.add(KeyStringValuePair.newBuilder().setKey("Process No.").setValue(getProcessNo() + "").build());
        osInfo.add(KeyStringValuePair.newBuilder().setKey("language").setValue("java").build());
        return osInfo;
    }
}
