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

package org.apache.skywalking.apm.plugin.jdbc;

import com.mysql.cj.api.jdbc.JdbcConnection;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class SWStatementTest extends AbstractStatementTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private com.mysql.cj.jdbc.StatementImpl mysqlStatement;
    @Mock
    private JdbcConnection jdbcConnection;
    private SWConnection swConnection;
    private SWConnection multiHostConnection;

    @Before
    public void setUp() throws Exception {
        swConnection = new SWConnection("jdbc:mysql://127.0.0.1:3306/test", new Properties(), jdbcConnection);
        multiHostConnection = new SWConnection("jdbc:mysql://127.0.0.1:3306,127.0.0.1:3309/test", new Properties(), jdbcConnection);

        when(jdbcConnection.createStatement()).thenReturn(mysqlStatement);
        when(jdbcConnection.createStatement(anyInt(), anyInt())).thenReturn(mysqlStatement);
        when(jdbcConnection.createStatement(anyInt(), anyInt(), anyInt())).thenReturn(mysqlStatement);
    }

    @Test
    public void testPreparedStatementConfig() throws SQLException {
        Statement statement = swConnection.createStatement();
        statement.cancel();
        statement.getUpdateCount();
        statement.setFetchDirection(1);
        statement.getFetchDirection();
        statement.getResultSetConcurrency();
        statement.getResultSetType();
        statement.isClosed();
        statement.setPoolable(false);
        statement.isPoolable();
        statement.getWarnings();
        statement.clearWarnings();
        statement.setCursorName("test");
        statement.setMaxFieldSize(11);
        statement.getMaxFieldSize();
        statement.setMaxRows(10);
        statement.getMaxRows();
        statement.setEscapeProcessing(true);
        statement.setFetchSize(1);
        statement.getFetchSize();
        statement.setQueryTimeout(1);
        statement.getQueryTimeout();
        Connection connection = statement.getConnection();

        statement.execute("SELECT * FROM test");
        statement.getMoreResults();
        statement.getMoreResults(1);
        statement.getResultSetHoldability();
        statement.getResultSet();

        statement.close();
        verify(mysqlStatement).getUpdateCount();
        verify(mysqlStatement).getMoreResults();
        verify(mysqlStatement).setFetchDirection(anyInt());
        verify(mysqlStatement).getFetchDirection();
        verify(mysqlStatement).getResultSetType();
        verify(mysqlStatement).isClosed();
        verify(mysqlStatement).setPoolable(anyBoolean());
        verify(mysqlStatement).getWarnings();
        verify(mysqlStatement).clearWarnings();
        verify(mysqlStatement).setCursorName(anyString());
        verify(mysqlStatement).setMaxFieldSize(anyInt());
        verify(mysqlStatement).getMaxFieldSize();
        verify(mysqlStatement).setMaxRows(anyInt());
        verify(mysqlStatement).getMaxRows();
        verify(mysqlStatement).setEscapeProcessing(anyBoolean());
        verify(mysqlStatement).getResultSetConcurrency();
        verify(mysqlStatement).getResultSetConcurrency();
        verify(mysqlStatement).getResultSetType();
        verify(mysqlStatement).getMoreResults(anyInt());
        verify(mysqlStatement).setFetchSize(anyInt());
        verify(mysqlStatement).getFetchSize();
        verify(mysqlStatement).getQueryTimeout();
        verify(mysqlStatement).setQueryTimeout(anyInt());
        verify(mysqlStatement).getResultSet();
        assertThat(connection, CoreMatchers.<Connection>is(swConnection));

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/Statement/execute", "SELECT * FROM test");
    }

    @Test
    public void testExecuteWithAutoGeneratedKey() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1);
        boolean executeSuccess = statement.execute("SELECT * FROM test", 1);

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/Statement/execute", "SELECT * FROM test");

    }

    @Test
    public void testExecuteQuery() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        ResultSet executeSuccess = statement.executeQuery("SELECT * FROM test");

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/Statement/executeQuery", "SELECT * FROM test");

    }

    @Test
    public void testExecuteUpdate() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        int executeSuccess = statement.executeUpdate("UPDATE test SET a = 1");

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/Statement/executeUpdate", "UPDATE test SET a = 1");

    }

    @Test
    public void testExecuteUpdateWithAutoGeneratedKey() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        int executeSuccess = statement.executeUpdate("UPDATE test SET a = 1", 1);
        statement.getGeneratedKeys();

        verify(mysqlStatement).getGeneratedKeys();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/Statement/executeUpdate", "UPDATE test SET a = 1");

    }

    @Test
    public void testExecuteUpdateWithColumnIndexes() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        int executeSuccess = statement.executeUpdate("UPDATE test SET a = 1", new int[] {1});

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/Statement/executeUpdate", "UPDATE test SET a = 1");

    }

    @Test
    public void testExecuteUpdateWithColumnStringIndexes() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        int executeSuccess = statement.executeUpdate("UPDATE test SET a = 1", new String[] {"1"});

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/Statement/executeUpdate", "UPDATE test SET a = 1");

    }

    @Test
    public void testExecuteWithColumnIndexes() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        boolean executeSuccess = statement.execute("UPDATE test SET a = 1", new int[] {1});

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/Statement/execute", "UPDATE test SET a = 1");

    }

    @Test
    public void testExecuteWithColumnStringIndexes() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        boolean executeSuccess = statement.execute("UPDATE test SET a = 1", new String[] {"1"});

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/Statement/execute", "UPDATE test SET a = 1");
    }

    @Test
    public void testBatch() throws SQLException, MalformedURLException {
        Statement statement = multiHostConnection.createStatement();
        statement.addBatch("UPDATE test SET a = 1 WHERE b = 2");
        int[] resultSet = statement.executeBatch();
        statement.clearBatch();

        verify(mysqlStatement).executeBatch();
        verify(mysqlStatement).addBatch(anyString());
        verify(mysqlStatement).clearBatch();

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/Statement/executeBatch", "");

    }

    @Test(expected = SQLException.class)
    public void testMultiHostWithException() throws SQLException {
        when(mysqlStatement.execute(anyString())).thenThrow(new SQLException());
        try {
            Statement statement = multiHostConnection.createStatement();
            statement.execute("UPDATE test SET a = 1 WHERE b = 2");
        } finally {
            verify(mysqlStatement).execute(anyString());
            TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
            List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
            assertThat(spans.size(), is(1));
            assertDBSpan(spans.get(0), "Mysql/JDBI/Statement/execute", "UPDATE test SET a = 1 WHERE b = 2");
            assertThat(SpanHelper.getLogs(spans.get(0)).size(), is(1));
            assertDBSpanLog(SpanHelper.getLogs(spans.get(0)).get(0));

        }
    }

}
