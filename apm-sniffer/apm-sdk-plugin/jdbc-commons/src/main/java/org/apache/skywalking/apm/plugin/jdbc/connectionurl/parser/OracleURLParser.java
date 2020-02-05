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


package org.apache.skywalking.apm.plugin.jdbc.connectionurl.parser;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * {@link OracleURLParser} presents that how to parse oracle connection url.
 *
 * Note: {@link OracleURLParser} can parse the commons connection url. the commons
 * connection url is of the form: <code>jdbc:oracle:(drivertype):@(database)</code>,the other
 * the form of connection url cannot be parsed success.
 *
 * @author zhangxin
 */
public class OracleURLParser extends AbstractURLParser {

    private static final String DB_TYPE = "Oracle";
    private static final int DEFAULT_PORT = 1521;
    public static final String SERVICE_NAME_FLAG = "@//";
    public static final String TNSNAME_URL_FLAG = "DESCRIPTION";

    public OracleURLParser(String url) {
        super(url);
    }

    @Override
    protected URLLocation fetchDatabaseHostsIndexRange() {
        int hostLabelStartIndex;
        if (isServiceNameURL()) {
            hostLabelStartIndex = url.indexOf(SERVICE_NAME_FLAG) + 3;
        } else {
            hostLabelStartIndex = url.indexOf("@") + 1;
        }
        int hostLabelEndIndex = url.lastIndexOf(":");
        return new URLLocation(hostLabelStartIndex, hostLabelEndIndex);
    }

    @Override
    protected URLLocation fetchDatabaseNameIndexRange() {
        int hostLabelStartIndex;
        int hostLabelEndIndex = url.length();
        if (isServiceNameURL()) {
            hostLabelStartIndex = url.lastIndexOf("/") + 1;
        } else if (isTNSNameURL()) {
            hostLabelStartIndex = url.indexOf("=", url.indexOf("SERVICE_NAME")) + 1;
            hostLabelEndIndex = url.indexOf(")", hostLabelStartIndex);
        } else {
            hostLabelStartIndex = url.lastIndexOf(":") + 1;
        }
        return new URLLocation(hostLabelStartIndex, hostLabelEndIndex);
    }

    private boolean isServiceNameURL() {
        return url.contains(SERVICE_NAME_FLAG);
    }

    private boolean isTNSNameURL() {
        return url.contains(TNSNAME_URL_FLAG);
    }

    @Override
    public ConnectionInfo parse() {
        if (isTNSNameURL()) {
            return tnsNameURLParse();
        } else {
            return commonsURLParse();
        }
    }

    private ConnectionInfo commonsURLParse() {
        String host = fetchDatabaseHostsFromURL();
        String[] hostSegment = splitDatabaseAddress(host);
        String databaseName = fetchDatabaseNameFromURL();
        if (hostSegment.length == 1) {
            return new ConnectionInfo(ComponentsDefine.OJDBC, DB_TYPE, host, DEFAULT_PORT, databaseName);
        } else {
            return new ConnectionInfo(ComponentsDefine.OJDBC, DB_TYPE, hostSegment[0], Integer.valueOf(hostSegment[1]), databaseName);
        }
    }

    private ConnectionInfo tnsNameURLParse() {
        String host = parseDatabaseHostsFromURL();
        String databaseName = fetchDatabaseNameFromURL();
        return new ConnectionInfo(ComponentsDefine.OJDBC, DB_TYPE, host, databaseName);
    }

    private String parseDatabaseHostsFromURL() {
        int beginIndex = url.indexOf("DESCRIPTION");
        List<String> hosts = new ArrayList<String>();
        do {
            int hostStartIndex = url.indexOf("HOST", beginIndex);
            if (hostStartIndex == -1) {
                break;
            }
            int equalStartIndex = url.indexOf("=", hostStartIndex);
            int hostEndIndex = url.indexOf(")", hostStartIndex);
            String host = url.substring(equalStartIndex + 1, hostEndIndex);

            int port = DEFAULT_PORT;
            int portStartIndex = url.indexOf("PORT", hostEndIndex);
            int portEndIndex = url.length();
            if (portStartIndex != -1) {
                int portEqualStartIndex = url.indexOf("=", portStartIndex);
                portEndIndex = url.indexOf(")", portEqualStartIndex);
                port = Integer.parseInt(url.substring(portEqualStartIndex + 1, portEndIndex).trim());
            }
            hosts.add(host.trim() + ":" + port);
            beginIndex = portEndIndex;
        }
        while (true);

        return StringUtil.join(',', hosts.toArray(new String[0]));
    }

    private String[] splitDatabaseAddress(String address) {
        String[] hostSegment = address.split(":");
        if (hostSegment.length == 1 && super.fetchDatabaseNameFromURL().contains("/")) {
            String[] portAndDatabaseName = super.fetchDatabaseNameFromURL().split("/");
            return new String[]{hostSegment[0], portAndDatabaseName[0]};
        } else {
            return hostSegment;
        }
    }

    @Override
    protected String fetchDatabaseNameFromURL() {
        String databaseName = super.fetchDatabaseNameFromURL();
        if (databaseName.contains("/")) {
            String[] portAndDatabaseName = databaseName.split("/");
            return portAndDatabaseName[1];
        } else {
            return databaseName;
        }
    }
}
