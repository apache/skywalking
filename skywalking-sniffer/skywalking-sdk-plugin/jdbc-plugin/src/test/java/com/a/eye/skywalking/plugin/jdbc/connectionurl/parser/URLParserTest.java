package com.a.eye.skywalking.plugin.jdbc.connectionurl.parser;

import com.a.eye.skywalking.plugin.jdbc.ConnectionInfo;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class URLParserTest {
    @Test
    public void testParseMysqlJDBCURLWithHost() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:mysql//primaryhost/test");
        assertThat(connectionInfo.getDBType(), is("Mysql"));
        assertThat(connectionInfo.getDatabaseName(), is("test"));
        assertThat(connectionInfo.getHost(), is("primaryhost"));
        assertThat(connectionInfo.getPort(), is(3306));
    }

    @Test
    public void testParseMysqlJDBCURLWithHostAndPort() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:mysql//primaryhost:3307/test?profileSQL=true");
        assertThat(connectionInfo.getDBType(), is("Mysql"));
        assertThat(connectionInfo.getDatabaseName(), is("test"));
        assertThat(connectionInfo.getHost(), is("primaryhost"));
        assertThat(connectionInfo.getPort(), is(3307));
    }

    @Test
    public void testParseMysqlJDBCURLWithMultiHost() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:mysql//primaryhost:3307,secondaryhost1,secondaryhost2/test?profileSQL=true");
        assertThat(connectionInfo.getDBType(), is("Mysql"));
        assertThat(connectionInfo.getDatabaseName(), is("test"));
        assertThat(connectionInfo.getHosts(), is("primaryhost:3307,secondaryhost1:3306,secondaryhost2:3306,"));
    }

    @Test
    public void testParseMysqlJDBCURLWithConnectorJs() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:mysql:replication://master,slave1,slave2,slave3/test");
        assertThat(connectionInfo.getDBType(), is("Mysql"));
        assertThat(connectionInfo.getDatabaseName(), is("test"));
        assertThat(connectionInfo.getHosts(), is("master:3306,slave1:3306,slave2:3306,slave3:3306,"));
    }

    @Test
    public void testParseOracleJDBCURLWithHost() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:oracle:thin:@localhost:orcl");
        assertThat(connectionInfo.getDBType(), is("Oracle"));
        assertThat(connectionInfo.getDatabaseName(), is("orcl"));
        assertThat(connectionInfo.getHost(), is("localhost"));
        assertThat(connectionInfo.getPort(), is(1521));
    }

    @Test
    public void testParseOracleJDBCURLWithHostAndPort() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:oracle:thin:@localhost:1522:orcl");
        assertThat(connectionInfo.getDBType(), is("Oracle"));
        assertThat(connectionInfo.getDatabaseName(), is("orcl"));
        assertThat(connectionInfo.getHost(), is("localhost"));
        assertThat(connectionInfo.getPort(), is(1522));
    }

    @Test
    public void testParseOracleJDBCURLWithUserNameAndPassword() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:oracle:thin:scott/tiger@myhost:1521:orcl");
        assertThat(connectionInfo.getDBType(), is("Oracle"));
        assertThat(connectionInfo.getDatabaseName(), is("orcl"));
        assertThat(connectionInfo.getHost(), is("myhost"));
        assertThat(connectionInfo.getPort(), is(1521));
    }

    @Test
    public void testParseH2JDBCURLWithEmbedded() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:h2:file:/data/sample");
        assertThat(connectionInfo.getDBType(), is("H2"));
        assertThat(connectionInfo.getDatabaseName(), is("/data/sample"));
        assertThat(connectionInfo.getHost(), is("localhost"));
        assertThat(connectionInfo.getPort(), is(-1));
    }

    @Test
    public void testParseH2JDBCURLWithEmbeddedRunningInWindows() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:h2:file:C:/data/sample");
        assertThat(connectionInfo.getDBType(), is("H2"));
        assertThat(connectionInfo.getDatabaseName(), is("C:/data/sample"));
        assertThat(connectionInfo.getHost(), is("localhost"));
        assertThat(connectionInfo.getPort(), is(-1));
    }

    @Test
    public void testParseH2JDBCURLWithMemoryMode() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:h2:mem:test_mem");
        assertThat(connectionInfo.getDBType(), is("H2"));
        assertThat(connectionInfo.getDatabaseName(), is("test_mem"));
        assertThat(connectionInfo.getHost(), is("localhost"));
        assertThat(connectionInfo.getPort(), is(-1));
    }

    @Test
    public void testParseH2JDBCURL() {
        ConnectionInfo connectionInfo = new URLParser().parser("jdbc:h2:tcp://localhost:8084/~/sample");
        assertThat(connectionInfo.getDBType(), is("H2"));
        assertThat(connectionInfo.getDatabaseName(), is("sample"));
        assertThat(connectionInfo.getHost(), is("localhost"));
        assertThat(connectionInfo.getPort(), is(8084));
    }
}