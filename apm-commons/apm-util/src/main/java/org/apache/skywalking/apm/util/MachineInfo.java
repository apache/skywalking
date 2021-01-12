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

package org.apache.skywalking.apm.util;

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
