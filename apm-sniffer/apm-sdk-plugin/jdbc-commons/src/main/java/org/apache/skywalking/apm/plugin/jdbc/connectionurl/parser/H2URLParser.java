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

import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

/**
 * {@link H2URLParser} presents that skywalking how to parse the connection url of H2 database.
 * <p>
 * {@link H2URLParser} check the connection url if contains "file" or "mem". if yes. the database name substring the
 * connection url from the index after "file" index or the "mem" index to the index of first charset ";".
 */
public class H2URLParser extends AbstractURLParser {

    private static final String LOCALHOST = "localhost";
    private static final int DEFAULT_PORT = 8084;
    /**
     * Flag that H2 running with file mode.
     */
    private static final String FILE_MODE_FLAG = "file";
    /**
     * Flag that H2 running with memory mode.
     */
    private static final String MEMORY_MODE_FLAG = "mem";
    private static final String H2_DB_TYPE = "H2";

    public H2URLParser(String url) {
        super(url);
    }

    @Override
    protected URLLocation fetchDatabaseHostsIndexRange() {
        int hostLabelStartIndex = url.indexOf("//");
        int hostLabelEndIndex = url.indexOf("/", hostLabelStartIndex + 2);
        return new URLLocation(hostLabelStartIndex + 2, hostLabelEndIndex);
    }

    @Override
    protected URLLocation fetchDatabaseNameIndexRange() {
        int databaseStartTag = url.lastIndexOf("/");
        int databaseEndTag = url.indexOf(";");
        if (databaseEndTag == -1) {
            databaseEndTag = url.length();
        }
        return new URLLocation(databaseStartTag + 1, databaseEndTag);
    }

    @Override
    public ConnectionInfo parse() {
        int[] databaseNameRangeIndex = fetchDatabaseNameRangeIndexFromURLForH2FileMode();
        if (databaseNameRangeIndex != null) {
            return new ConnectionInfo(ComponentsDefine.H2_JDBC_DRIVER, H2_DB_TYPE, LOCALHOST, -1, fetchDatabaseNameFromURL(databaseNameRangeIndex));
        }

        databaseNameRangeIndex = fetchDatabaseNameRangeIndexFromURLForH2MemMode();
        if (databaseNameRangeIndex != null) {
            return new ConnectionInfo(ComponentsDefine.H2_JDBC_DRIVER, H2_DB_TYPE, LOCALHOST, -1, fetchDatabaseNameFromURL(databaseNameRangeIndex));
        }

        String[] hostAndPort = fetchDatabaseHostsFromURL().split(":");
        if (hostAndPort.length == 1) {
            return new ConnectionInfo(ComponentsDefine.H2_JDBC_DRIVER, H2_DB_TYPE, hostAndPort[0], DEFAULT_PORT, fetchDatabaseNameFromURL());
        } else {
            return new ConnectionInfo(ComponentsDefine.H2_JDBC_DRIVER, H2_DB_TYPE, hostAndPort[0], Integer.valueOf(hostAndPort[1]), fetchDatabaseNameFromURL());
        }
    }

    /**
     * Fetch range index that the database name from connection url if H2 database running with file mode.
     *
     * @return range index that the database name.
     */
    private int[] fetchDatabaseNameRangeIndexFromURLForH2FileMode() {
        int fileLabelIndex = url.indexOf(FILE_MODE_FLAG);
        int parameterLabelIndex = url.indexOf(";", fileLabelIndex);
        if (parameterLabelIndex == -1) {
            parameterLabelIndex = url.length();
        }

        if (fileLabelIndex != -1) {
            return new int[] {
                fileLabelIndex + FILE_MODE_FLAG.length() + 1,
                parameterLabelIndex
            };
        } else {
            return null;
        }
    }

    /**
     * Fetch range index that the database name from connection url if H2 database running with memory mode.
     *
     * @return range index that the database name.
     */
    private int[] fetchDatabaseNameRangeIndexFromURLForH2MemMode() {
        int fileLabelIndex = url.indexOf(MEMORY_MODE_FLAG);
        int parameterLabelIndex = url.indexOf(";", fileLabelIndex);
        if (parameterLabelIndex == -1) {
            parameterLabelIndex = url.length();
        }

        if (fileLabelIndex != -1) {
            return new int[] {
                fileLabelIndex + MEMORY_MODE_FLAG.length() + 1,
                parameterLabelIndex
            };
        } else {
            return null;
        }
    }
}
