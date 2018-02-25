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
}