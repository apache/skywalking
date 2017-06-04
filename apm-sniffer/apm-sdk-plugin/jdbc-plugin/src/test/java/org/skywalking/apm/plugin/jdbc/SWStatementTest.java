package org.skywalking.apm.plugin.jdbc;

import com.mysql.cj.api.jdbc.JdbcConnection;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.context.TracingContext;
import org.skywalking.apm.sniffer.mock.context.MockTracerContextListener;
import org.skywalking.apm.sniffer.mock.context.SegmentAssert;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.TraceSegment;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SWStatementTest extends AbstractStatementTest {
    @Mock
    private com.mysql.cj.jdbc.StatementImpl mysqlStatement;
    @Mock
    private JdbcConnection jdbcConnection;
    private SWConnection swConnection;
    private SWConnection multiHostConnection;

    @Before
    public void setUp() throws Exception {
        mockTracerContextListener = new MockTracerContextListener();

        ServiceManager.INSTANCE.boot();
        swConnection = new SWConnection("jdbc:mysql://127.0.0.1:3306/test", new Properties(), jdbcConnection);
        multiHostConnection = new SWConnection("jdbc:mysql://127.0.0.1:3306,127.0.0.1:3309/test", new Properties(), jdbcConnection);

        TracingContext.ListenerManager.add(mockTracerContextListener);

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
        verify(mysqlStatement, times(1)).getUpdateCount();
        verify(mysqlStatement, times(1)).getMoreResults();
        verify(mysqlStatement, times(1)).setFetchDirection(anyInt());
        verify(mysqlStatement, times(1)).getFetchDirection();
        verify(mysqlStatement, times(1)).getResultSetType();
        verify(mysqlStatement, times(1)).isClosed();
        verify(mysqlStatement, times(1)).setPoolable(anyBoolean());
        verify(mysqlStatement, times(1)).getWarnings();
        verify(mysqlStatement, times(1)).clearWarnings();
        verify(mysqlStatement, times(1)).setCursorName(anyString());
        verify(mysqlStatement, times(1)).setMaxFieldSize(anyInt());
        verify(mysqlStatement, times(1)).getMaxFieldSize();
        verify(mysqlStatement, times(1)).setMaxRows(anyInt());
        verify(mysqlStatement, times(1)).getMaxRows();
        verify(mysqlStatement, times(1)).setEscapeProcessing(anyBoolean());
        verify(mysqlStatement, times(1)).getResultSetConcurrency();
        verify(mysqlStatement, times(1)).getResultSetConcurrency();
        verify(mysqlStatement, times(1)).getResultSetType();
        verify(mysqlStatement, times(1)).getMoreResults(anyInt());
        verify(mysqlStatement, times(1)).setFetchSize(anyInt());
        verify(mysqlStatement, times(1)).getFetchSize();
        verify(mysqlStatement, times(1)).getQueryTimeout();
        verify(mysqlStatement, times(1)).setQueryTimeout(anyInt());
        verify(mysqlStatement, times(1)).getResultSet();
        assertThat(connection, CoreMatchers.<Connection>is(swConnection));

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertDBSpan(span, "Mysql/JDBI/Statement/execute", "SELECT * FROM test");
            }
        });
    }

    @Test
    public void testExecuteWithAutoGeneratedKey() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1);
        boolean executeSuccess = statement.execute("SELECT * FROM test", 1);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertDBSpan(span, "Mysql/JDBI/Statement/execute", "SELECT * FROM test");
            }
        });
    }

    @Test
    public void testExecuteQuery() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        ResultSet executeSuccess = statement.executeQuery("SELECT * FROM test");

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertDBSpan(span, "Mysql/JDBI/Statement/executeQuery", "SELECT * FROM test");
            }
        });
    }

    @Test
    public void testExecuteUpdate() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        int executeSuccess = statement.executeUpdate("UPDATE test SET a = 1");

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertDBSpan(span, "Mysql/JDBI/Statement/executeUpdate", "UPDATE test SET a = 1");
            }
        });
    }

    @Test
    public void testExecuteUpdateWithAutoGeneratedKey() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        int executeSuccess = statement.executeUpdate("UPDATE test SET a = 1", 1);
        statement.getGeneratedKeys();

        verify(mysqlStatement, times(1)).getGeneratedKeys();
        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertDBSpan(span, "Mysql/JDBI/Statement/executeUpdate", "UPDATE test SET a = 1");
            }
        });
    }

    @Test
    public void testExecuteUpdateWithColumnIndexes() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        int executeSuccess = statement.executeUpdate("UPDATE test SET a = 1", new int[] {1});

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertDBSpan(span, "Mysql/JDBI/Statement/executeUpdate", "UPDATE test SET a = 1");
            }
        });
    }

    @Test
    public void testExecuteUpdateWithColumnStringIndexes() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        int executeSuccess = statement.executeUpdate("UPDATE test SET a = 1", new String[] {"1"});

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertDBSpan(span, "Mysql/JDBI/Statement/executeUpdate", "UPDATE test SET a = 1");
            }
        });
    }

    @Test
    public void testExecuteWithColumnIndexes() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        boolean executeSuccess = statement.execute("UPDATE test SET a = 1", new int[] {1});

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertDBSpan(span, "Mysql/JDBI/Statement/execute", "UPDATE test SET a = 1");
            }
        });
    }

    @Test
    public void testExecuteWithColumnStringIndexes() throws SQLException {
        Statement statement = swConnection.createStatement(1, 1, 1);
        boolean executeSuccess = statement.execute("UPDATE test SET a = 1", new String[] {"1"});

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertDBSpan(span, "Mysql/JDBI/Statement/execute", "UPDATE test SET a = 1");
            }
        });
    }

    @Test
    public void testBatch() throws SQLException, MalformedURLException {
        Statement statement = multiHostConnection.createStatement();
        statement.addBatch("UPDATE test SET a = 1 WHERE b = 2");
        int[] resultSet = statement.executeBatch();
        statement.clearBatch();

        verify(mysqlStatement, times(1)).executeBatch();
        verify(mysqlStatement, times(1)).addBatch(anyString());
        verify(mysqlStatement, times(1)).clearBatch();

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertDBSpan(span, "Mysql/JDBI/Statement/executeBatch", "");
            }
        });
    }

    @Test(expected = SQLException.class)
    public void testMultiHostWithException() throws SQLException {
        when(mysqlStatement.execute(anyString())).thenThrow(new SQLException());
        try {
            Statement statement = multiHostConnection.createStatement();
            statement.execute("UPDATE test SET a = 1 WHERE b = 2");
        } finally {
            verify(mysqlStatement, times(1)).execute(anyString());
            mockTracerContextListener.assertSize(1);
            mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
                @Override
                public void call(TraceSegment traceSegment) {
                    assertThat(traceSegment.getSpans().size(), is(1));
                    Span span = traceSegment.getSpans().get(0);
                    assertDBSpan(span, "Mysql/JDBI/Statement/execute", "UPDATE test SET a = 1 WHERE b = 2");
                    assertThat(span.getLogs().size(), is(1));
                    assertDBSpanLog(span.getLogs().get(0));
                }
            });
        }
    }

    @After
    public void tearDown() throws Exception {
        TracingContext.ListenerManager.remove(mockTracerContextListener);
    }
}
