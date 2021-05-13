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

import org.junit.Test;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

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
    public void testParseMysqlJDBCURLWithoutDB() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:mysql//primaryhost?profileSQL=true");
        assertThat(connectionInfo.getDBType(), is("Mysql"));
        assertThat(connectionInfo.getDatabaseName(), is(""));
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
        assertThat(connectionInfo.getDatabasePeer(), is("primaryhost:3307,secondaryhost1:3306,secondaryhost2:3306"));
    }

    @Test
    public void testParseMysqlJDBCURLWithConnectorJs() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:mysql:replication://master,slave1,slave2,slave3/test");
        assertThat(connectionInfo.getDBType(), is("Mysql"));
        assertThat(connectionInfo.getDatabaseName(), is("test"));
        assertThat(connectionInfo.getDatabasePeer(), is("master:3306,slave1:3306,slave2:3306,slave3:3306"));
    }

    @Test
    public void testParseOracleJDBCURLWithHost() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:oracle:thin:@localhost:orcl");
        assertThat(connectionInfo.getDBType(), is("Oracle"));
        assertThat(connectionInfo.getDatabaseName(), is("orcl"));
        assertThat(connectionInfo.getDatabasePeer(), is("localhost:1521"));
    }

    @Test
    public void testParseOracleJDBCURLWithHostAndPort() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:oracle:thin:@localhost:1522:orcl");
        assertThat(connectionInfo.getDBType(), is("Oracle"));
        assertThat(connectionInfo.getDatabaseName(), is("orcl"));
        assertThat(connectionInfo.getDatabasePeer(), is("localhost:1522"));
    }

    @Test
    public void testParseOracleSID() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:oracle:thin:@localhost:1522/orcl");
        assertThat(connectionInfo.getDBType(), is("Oracle"));
        assertThat(connectionInfo.getDatabaseName(), is("orcl"));
        assertThat(connectionInfo.getDatabasePeer(), is("localhost:1522"));
    }

    @Test
    public void testParseOracleServiceName() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:oracle:thin:@//localhost:1521/orcl");
        assertThat(connectionInfo.getDBType(), is("Oracle"));
        assertThat(connectionInfo.getDatabaseName(), is("orcl"));
        assertThat(connectionInfo.getDatabasePeer(), is("localhost:1521"));
    }

    @Test
    public void testParseOracleTNSName() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST= localhost )(PORT= 1521))(CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=orcl)))");
        assertThat(connectionInfo.getDBType(), is("Oracle"));
        assertThat(connectionInfo.getDatabaseName(), is("orcl"));
        assertThat(connectionInfo.getDatabasePeer(), is("localhost:1521"));
    }

    @Test
    public void testParseOracleTNSNameWithMultiAddress() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS_LIST=(ADDRESS=(PROTOCOL= TCP)(HOST=hostA)(PORT= 1523 ))(ADDRESS=(PROTOCOL=TCP)(HOST=hostB)(PORT= 1521 )))(SOURCE_ROUTE=yes)(CONNECT_DATA=(SERVICE_NAME=orcl)))");
        assertThat(connectionInfo.getDBType(), is("Oracle"));
        assertThat(connectionInfo.getDatabaseName(), is("orcl"));
        assertThat(connectionInfo.getDatabasePeer(), is("hostA:1523,hostB:1521"));
    }

    @Test
    public void testParseOracleJDBCURLWithUserNameAndPassword() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:oracle:thin:scott/tiger@myhost:1521:orcl");
        assertThat(connectionInfo.getDBType(), is("Oracle"));
        assertThat(connectionInfo.getDatabaseName(), is("orcl"));
        assertThat(connectionInfo.getDatabasePeer(), is("myhost:1521"));
    }

    @Test
    public void testParseH2JDBCURLWithEmbedded() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:h2:file:/data/sample");
        assertThat(connectionInfo.getDBType(), is("H2"));
        assertThat(connectionInfo.getDatabaseName(), is("/data/sample"));
        assertThat(connectionInfo.getDatabasePeer(), is("localhost:-1"));
    }

    @Test
    public void testParseH2JDBCURLWithEmbeddedRunningInWindows() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:h2:file:C:/data/sample");
        assertThat(connectionInfo.getDBType(), is("H2"));
        assertThat(connectionInfo.getDatabaseName(), is("C:/data/sample"));
        assertThat(connectionInfo.getDatabasePeer(), is("localhost:-1"));
    }

    @Test
    public void testParseH2JDBCURLWithMemoryMode() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:h2:mem:test_mem");
        assertThat(connectionInfo.getDBType(), is("H2"));
        assertThat(connectionInfo.getDatabaseName(), is("test_mem"));
        assertThat(connectionInfo.getDatabasePeer(), is("localhost:-1"));
    }

    @Test
    public void testParseH2JDBCURL() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:h2:tcp://localhost:8084/~/sample");
        assertThat(connectionInfo.getDBType(), is("H2"));
        assertThat(connectionInfo.getDatabaseName(), is("sample"));
        assertThat(connectionInfo.getDatabasePeer(), is("localhost:8084"));
    }

    @Test
    public void testParseMariadbJDBCURLWithHost() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:mariadb//primaryhost/test");
        assertThat(connectionInfo.getDBType(), is("Mariadb"));
        assertThat(connectionInfo.getDatabaseName(), is("test"));
        assertThat(connectionInfo.getDatabasePeer(), is("primaryhost:3306"));
    }
}
