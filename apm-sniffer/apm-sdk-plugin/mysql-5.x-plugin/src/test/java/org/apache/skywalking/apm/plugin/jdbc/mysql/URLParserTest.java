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

import org.apache.skywalking.apm.plugin.jdbc.connectionurl.parser.URLParser;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class URLParserTest {
    @Test
    public void testParseMysqlJDBCURLWithHost() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:mysql//primaryhost/test");
        assertThat(connectionInfo.getDBType(), is("Mysql"));
        assertThat(connectionInfo.getDatabaseName(), is("test"));
        assertThat(connectionInfo.getDatabasePeer(), is("primaryhost:3306"));
    }

    @Test
    public void testParseMysqlJDBCURLWithHostAndPort() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:mysql//primaryhost:3307/test?profileSQL=true");
        assertThat(connectionInfo.getDBType(), is("Mysql"));
        assertThat(connectionInfo.getDatabaseName(), is("test"));
        assertThat(connectionInfo.getDatabasePeer(), is("primaryhost:3307"));
    }

    @Test
    public void testParseMysqlJDBCURLWithMultiHost() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:mysql//primaryhost:3307,secondaryhost1,secondaryhost2/test?profileSQL=true");
        assertThat(connectionInfo.getDBType(), is("Mysql"));
        assertThat(connectionInfo.getDatabaseName(), is("test"));
        assertThat(connectionInfo.getDatabasePeer(), is("primaryhost:3307,secondaryhost1:3306,secondaryhost2:3306,"));
    }

    @Test
    public void testParseMysqlJDBCURLWithConnectorJs() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:mysql:replication://master,slave1,slave2,slave3/test");
        assertThat(connectionInfo.getDBType(), is("Mysql"));
        assertThat(connectionInfo.getDatabaseName(), is("test"));
        assertThat(connectionInfo.getDatabasePeer(), is("master:3306,slave1:3306,slave2:3306,slave3:3306,"));
    }

}
