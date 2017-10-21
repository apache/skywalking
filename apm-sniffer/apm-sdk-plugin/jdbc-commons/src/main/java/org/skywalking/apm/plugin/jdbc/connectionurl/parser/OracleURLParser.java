/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.jdbc.connectionurl.parser;

import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

/**
 * {@link OracleURLParser} presents that how to parse oracle connection url.
 * <p>
 * The {@link ConnectionInfo#host} be set the string between charset "@" and the last
 * charset ":" after the charset "@", and {@link ConnectionInfo#databaseName} be set the
 * string that after the last index of ":".
 * <p>
 * Note: {@link OracleURLParser} can parse the commons connection url. the commons
 * connection url is of the form: <code>jdbc:oracle:<drivertype>:@<database></code>,the other
 * the form of connection url cannot be parsed success.
 *
 * @author zhangxin
 */
public class OracleURLParser extends AbstractURLParser {

    private static final String DB_TYPE = "Oracle";
    private static final int DEFAULT_PORT = 1521;

    public OracleURLParser(String url) {
        super(url);
    }

    @Override
    protected int[] fetchDatabaseHostsIndexRange() {
        int hostLabelStartIndex = url.indexOf("@");
        int hostLabelEndIndex = url.lastIndexOf(":");
        return new int[] {hostLabelStartIndex + 1, hostLabelEndIndex};
    }

    @Override
    protected int[] fetchDatabaseNameIndexRange() {
        return new int[0];
    }

    @Override
    public ConnectionInfo parse() {
        int[] hostRangeIndex = fetchDatabaseHostsIndexRange();
        String host = fetchDatabaseHostsFromURL();
        String[] hostSegment = splitDatabaseAddress(host);
        String databaseName = url.substring(hostRangeIndex[1] + 1);
        if (hostSegment.length == 1) {
            return new ConnectionInfo(ComponentsDefine.ORACLE, DB_TYPE, host, DEFAULT_PORT, databaseName);
        } else {
            return new ConnectionInfo(ComponentsDefine.ORACLE, DB_TYPE, hostSegment[0], Integer.valueOf(hostSegment[1]), databaseName);
        }
    }

    private String[] splitDatabaseAddress(String address) {
        String[] hostSegment = address.split(":");
        return hostSegment;
    }
}
