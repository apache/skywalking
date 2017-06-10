package org.skywalking.apm.plugin.jdbc;

import com.mysql.cj.api.jdbc.JdbcConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.context.TracerContext;
import org.skywalking.apm.sniffer.mock.context.MockTracerContextListener;
import org.skywalking.apm.sniffer.mock.context.SegmentAssert;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.Executor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SWConnectionTest extends AbstractStatementTest {
    @Mock
    private com.mysql.cj.jdbc.PreparedStatement mysqlPreparedStatement;
    @Mock
    private JdbcConnection jdbcConnection;
    @Mock
    private Executor executor;
    @Mock
    private Savepoint savepoint;
    private SWConnection swConnection;
    private SWConnection multiHostConnection;

    @Before
    public void setUp() throws Exception {
        ServiceManager.INSTANCE.boot();
        mockTracerContextListener = new MockTracerContextListener();
        swConnection = new SWConnection("jdbc:mysql://127.0.0.1:3306/test", new Properties(), jdbcConnection);
        multiHostConnection = new SWConnection("jdbc:mysql://127.0.0.1:3306,127.0.0.1:3309/test", new Properties(), jdbcConnection);

        TracerContext.ListenerManager.add(mockTracerContextListener);

        when(jdbcConnection.prepareStatement(anyString())).thenReturn(mysqlPreparedStatement);
    }

    @Test
    public void testCommit() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("SELECT * FROM test");

        swConnection.commit();

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/commit");
            }
        });
    }

    @Test
    public void testMultiHostCommit() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("SELECT * FROM test", new String[] {"1"});
        multiHostConnection.commit();

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/commit");
            }
        });
    }

    @Test(expected = SQLException.class)
    public void testCommitWithException() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("SELECT * FROM test", new int[] {1});
        doThrow(new SQLException()).when(jdbcConnection).commit();
        try {
            swConnection.commit();
        } finally {
            mockTracerContextListener.assertSize(1);
            mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
                @Override
                public void call(TraceSegment traceSegment) {
                    assertThat(traceSegment.getSpans().size(), is(1));
                    assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/commit");
                    try {
                        assertDBSpanLog(getLogs(traceSegment.getSpans().get(0)).get(0));
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException(e);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    @Test
    public void testRollBack() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("SELECT * FROM test", 1, 1);
        swConnection.rollback();

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/rollback");

            }
        });
    }

    @Test
    public void testMultiHostRollBack() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("SELECT * FROM test", 1, 1, 1);
        multiHostConnection.rollback();

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/rollback");
            }
        });
    }

    @Test(expected = SQLException.class)
    public void testRollBackWithException() throws SQLException {
        doThrow(new SQLException()).when(jdbcConnection).rollback();

        swConnection.rollback();

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/rollback");
            }
        });
    }

    @Test
    public void testRollBackWithSavePoint() throws SQLException {
        swConnection.rollback(savepoint);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/rollback to savepoint");
            }
        });
    }

    @Test
    public void testMultiHostRollBackWithSavePoint() throws SQLException {
        multiHostConnection.rollback(savepoint);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/rollback to savepoint");
            }
        });
    }

    @Test(expected = SQLException.class)
    public void testRollBackWithSavePointWithException() throws SQLException {
        doThrow(new SQLException()).when(jdbcConnection).rollback(any(Savepoint.class));

        swConnection.rollback(savepoint);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/rollback to savepoint");
                try {
                    assertDBSpanLog(getLogs(traceSegment.getSpans().get(0)).get(0));
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void testClose() throws SQLException {
        swConnection.close();
        swConnection.clearWarnings();

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/close");
            }
        });
    }

    @Test
    public void testMultiHostClose() throws SQLException {
        multiHostConnection.close();

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/close");
            }
        });
    }

    @Test(expected = SQLException.class)
    public void testCloseWithException() throws SQLException {
        doThrow(new SQLException()).when(jdbcConnection).close();

        swConnection.close();

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/close");
                try {
                    assertDBSpanLog(getLogs(traceSegment.getSpans().get(0)).get(0));
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void testReleaseSavePoint() throws SQLException {
        swConnection.releaseSavepoint(savepoint);
        swConnection.clearWarnings();

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/releaseSavepoint savepoint");
            }
        });
    }

    @Test
    public void testMultiHostReleaseSavePoint() throws SQLException {
        multiHostConnection.releaseSavepoint(savepoint);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/releaseSavepoint savepoint");
            }
        });
    }

    @Test(expected = SQLException.class)
    public void testReleaseSavePointWithException() throws SQLException {
        doThrow(new SQLException()).when(jdbcConnection).releaseSavepoint(any(Savepoint.class));

        swConnection.releaseSavepoint(savepoint);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertDBSpan(traceSegment.getSpans().get(0), "Mysql/JDBI/Connection/releaseSavepoint savepoint");
                try {
                    assertDBSpanLog(getLogs(traceSegment.getSpans().get(0)).get(0));
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void testSetConfig() throws SQLException {
        swConnection.createArrayOf("1", new Object[0]);
        swConnection.createBlob();
        swConnection.createClob();
        swConnection.createNClob();
        swConnection.createSQLXML();
        swConnection.nativeSQL("SELECT IT");
        swConnection.setAutoCommit(true);
        swConnection.getAutoCommit();
        swConnection.setCatalog("test");
        swConnection.getCatalog();
        swConnection.setClientInfo(new Properties());
        swConnection.getClientInfo();
        swConnection.setHoldability(1);
        swConnection.getHoldability();
        swConnection.setReadOnly(false);
        swConnection.setClientInfo("test-client", "test-client");
        swConnection.getClientInfo("test");
        swConnection.setSavepoint();
        swConnection.getMetaData();
        swConnection.getTransactionIsolation();
        swConnection.getTypeMap();
        swConnection.getWarnings();
        swConnection.isClosed();
        swConnection.isReadOnly();
        swConnection.isValid(10);
        swConnection.setSavepoint("test");
        swConnection.setTransactionIsolation(1);
        swConnection.setTypeMap(new HashMap<String, Class<?>>());

        verify(jdbcConnection, times(1)).createBlob();
        verify(jdbcConnection, times(1)).createClob();
        verify(jdbcConnection, times(1)).createNClob();
        verify(jdbcConnection, times(1)).createSQLXML();
        verify(jdbcConnection, times(1)).nativeSQL(anyString());
        verify(jdbcConnection, times(1)).setAutoCommit(anyBoolean());
        verify(jdbcConnection, times(1)).getAutoCommit();
        verify(jdbcConnection, times(1)).setCatalog(anyString());
        verify(jdbcConnection, times(1)).getCatalog();
        verify(jdbcConnection, times(1)).setClientInfo(anyString(), anyString());
        verify(jdbcConnection, times(1)).setHoldability(anyInt());
        verify(jdbcConnection, times(1)).getHoldability();
        verify(jdbcConnection, times(1)).setReadOnly(anyBoolean());
        verify(jdbcConnection, times(1)).getClientInfo();
        verify(jdbcConnection, times(1)).getClientInfo(anyString());
        verify(jdbcConnection, times(1)).setSavepoint(anyString());
        verify(jdbcConnection, times(1)).setSavepoint();
        verify(jdbcConnection, times(1)).getMetaData();
        verify(jdbcConnection, times(1)).getTransactionIsolation();
        verify(jdbcConnection, times(1)).getTypeMap();
        verify(jdbcConnection, times(1)).getWarnings();
        verify(jdbcConnection, times(1)).setTransactionIsolation(anyInt());
        verify(jdbcConnection, times(1)).getTransactionIsolation();
        verify(jdbcConnection, times(1)).isClosed();
        verify(jdbcConnection, times(1)).isReadOnly();
        verify(jdbcConnection, times(1)).isValid(anyInt());
        verify(jdbcConnection, times(1)).setTypeMap(any(HashMap.class));

    }

    @After
    public void tearDown() throws Exception {
        TracerContext.ListenerManager.remove(mockTracerContextListener);
    }

}
