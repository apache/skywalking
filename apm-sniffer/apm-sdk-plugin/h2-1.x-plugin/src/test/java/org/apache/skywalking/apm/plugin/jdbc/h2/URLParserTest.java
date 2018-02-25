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


package org.apache.skywalking.apm.plugin.jdbc.h2;

import org.apache.skywalking.apm.plugin.jdbc.connectionurl.parser.URLParser;
import org.junit.Test;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class URLParserTest {
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
}