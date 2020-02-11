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

package org.apache.skywalking.apm.plugin.jdbc.mysql;

import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;
import org.apache.skywalking.apm.util.StringUtil;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionCache {
    private static final ConcurrentHashMap<String, ConnectionInfo> CONNECTIONS_MAP = new ConcurrentHashMap<String, ConnectionInfo>();

    private static final String CONNECTION_SPLIT_STR = ",";

    public static ConnectionInfo get(String host, String port) {
        final String connStr = String.format("%s:%s", host, port);
        return CONNECTIONS_MAP.get(connStr);
    }

    public static void save(ConnectionInfo connectionInfo) {
        for (String conn : connectionInfo.getDatabasePeer().split(CONNECTION_SPLIT_STR)) {
            if (!StringUtil.isEmpty(conn)) {
                CONNECTIONS_MAP.putIfAbsent(conn, connectionInfo);
            }
        }
    }
}
