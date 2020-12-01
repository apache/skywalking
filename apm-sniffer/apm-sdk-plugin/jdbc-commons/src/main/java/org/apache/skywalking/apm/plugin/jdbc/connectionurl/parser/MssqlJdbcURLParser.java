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

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.network.trace.component.OfficialComponent;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

/**
 * {@link MssqlJdbcURLParser} parse connection url of mssql.
 */
public class MssqlJdbcURLParser extends AbstractURLParser {

    private static final int DEFAULT_PORT = 1433;
    private String dbType = "Mssql";
    private static final String DATABASE_NAME_PROPERTY = "databaseName=";
    private OfficialComponent component = ComponentsDefine.MSSQL_JDBC_DRIVER;
    private static ILog LOGGER = LogManager.getLogger(MssqlJdbcURLParser.class);

    public MssqlJdbcURLParser(String url) {
        super(url);
    }

    @Override
    protected URLLocation fetchDatabaseHostsIndexRange() {
        int hostLabelStartIndex = url.indexOf("//");
        int hostLabelEndIndex = url.indexOf(";", hostLabelStartIndex + 2);
        if (hostLabelEndIndex == -1) {
            hostLabelEndIndex = url.indexOf("/", hostLabelStartIndex + 2);
        }
        return new URLLocation(hostLabelStartIndex + 2, hostLabelEndIndex);
    }

    protected String fetchDatabaseNameFromURL(int startSize) {
        URLLocation hostsLocation = fetchDatabaseNameIndexRange();
        if (hostsLocation == null) {
            return "";
        }
        return url.substring(hostsLocation.startIndex(), hostsLocation.endIndex());
    }

    protected URLLocation fetchDatabaseNameIndexRange(int startSize) {
        return fetchDatabaseNameIndexRange();
    }

    @Override
    protected URLLocation fetchDatabaseNameIndexRange() {
        String urlTemp = url.toLowerCase();
        int databaseStartTag = urlTemp.indexOf(DATABASE_NAME_PROPERTY.toLowerCase());
        int databaseEndTag = url.indexOf(";", databaseStartTag);
        if (databaseEndTag == -1) {
            databaseEndTag = url.length();
        }
        return new URLLocation(databaseStartTag + DATABASE_NAME_PROPERTY.length(), databaseEndTag);
    }

    @Override
    public ConnectionInfo parse() {
        try {
            URLLocation location = fetchDatabaseHostsIndexRange();
            String hosts = url.substring(location.startIndex(), location.endIndex());
            String[] hostSegment = hosts.split(",");
            if (hostSegment.length > 1) {
                StringBuilder sb = new StringBuilder();
                for (String host : hostSegment) {
                    if (host.split(":").length == 1) {
                        sb.append(host).append(":").append(DEFAULT_PORT).append(",");
                    } else {
                        sb.append(host).append(",");
                    }
                }
                return new ConnectionInfo(
                    component, dbType, sb.substring(0, sb.length() - 1), fetchDatabaseNameFromURL());
            } else {
                String[] hostAndPort = hostSegment[0].split(":");
                if (hostAndPort.length != 1) {
                    return new ConnectionInfo(
                        component, dbType, hostAndPort[0], Integer.valueOf(hostAndPort[1]),
                        fetchDatabaseNameFromURL(location.endIndex())
                    );
                } else {
                    return new ConnectionInfo(
                        component, dbType, hostAndPort[0], DEFAULT_PORT, fetchDatabaseNameFromURL(location
                                                                                                      .endIndex()));
                }
            }
        } catch (Exception e) {
            LOGGER.error(e, "parse mssql connection info error, url:{}", url);
            return new ConnectionInfo(component, dbType, url, "UNKNOWN");
        }
    }
}
