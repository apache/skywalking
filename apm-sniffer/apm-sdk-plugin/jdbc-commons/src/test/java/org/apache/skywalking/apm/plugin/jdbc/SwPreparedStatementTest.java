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
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyByte;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyShort;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class SwPreparedStatementTest extends AbstractStatementTest {
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private Array array;
    @Mock
    private SQLXML sqlxml;
    @Mock
    private RowId rowId;
    @Mock
    private Ref ref;
    @Mock
    private Clob clob;
    @Mock
    private NClob nClob;
    @Mock
    private Reader reader;
    @Mock
    private InputStream inputStream;
    @Mock
    private Blob blob;
    @Mock
    private com.mysql.cj.jdbc.PreparedStatement mysqlPreparedStatement;
    @Mock
    private JdbcConnection jdbcConnection;
    private SWConnection swConnection;
    private SWConnection multiHostConnection;
    private byte[] bytesParam = new byte[] {
        1,
        2
    };

    @Before
    public void setUp() throws Exception {
        swConnection = new SWConnection("jdbc:mysql://127.0.0.1:3306/test", new Properties(), jdbcConnection);
        multiHostConnection = new SWConnection("jdbc:mysql://127.0.0.1:3306,127.0.0.1:3309/test", new Properties(), jdbcConnection);

        when(jdbcConnection.prepareStatement(anyString())).thenReturn(mysqlPreparedStatement);
        when(jdbcConnection.prepareStatement(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(mysqlPreparedStatement);
        when(jdbcConnection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(mysqlPreparedStatement);
        when(jdbcConnection.prepareStatement(anyString(), anyInt())).thenReturn(mysqlPreparedStatement);
    }

    @Test
    public void testSetParam() throws SQLException, MalformedURLException {
        PreparedStatement preparedStatement = multiHostConnection.prepareStatement("SELECT * FROM test WHERE a = ? or b = ? or c=? or d = ? or e = ?" + " or e = ? or f = ? or g = ? or h = ? or i = ? or j = ? or k = ? or l = ? or m = ?  or n = ? or o = ? or p = ? " + " or r = ?  or s = ? or t = ?  or u = ?  or v = ?  or w = ?  or x = ?  or y = ? or z = ? or a1 = ? or a2 = ? or a3 = ?" + " or a4 = ? or a5 = ? or a6 = ?  or a7 = ?  or a8 = ?  or a9 = ? or b1 = ? or b2 = ? or b3 = ? or b4 = ? or b5 = ?" + " or b6 = ? or b7 = ? or b8  = ? or b9 = ? or c1 = ?  or c2 = ? or c3 = ?");
        preparedStatement.clearParameters();
        preparedStatement.setAsciiStream(1, inputStream);
        preparedStatement.setAsciiStream(2, inputStream, 10);
        preparedStatement.setAsciiStream(3, inputStream, 1000000L);
        preparedStatement.setCharacterStream(4, reader);
        preparedStatement.setCharacterStream(4, reader, 10);
        preparedStatement.setCharacterStream(5, reader, 10L);
        preparedStatement.setShort(6, (short) 12);
        preparedStatement.setInt(7, 1);
        preparedStatement.setString(8, "test");
        preparedStatement.setBoolean(9, true);
        preparedStatement.setLong(10, 100L);
        preparedStatement.setDouble(11, 12.0);
        preparedStatement.setFloat(12, 12.0f);
        preparedStatement.setByte(13, (byte) 1);
        preparedStatement.setBytes(14, bytesParam);
        preparedStatement.setDate(15, new Date(System.currentTimeMillis()));
        preparedStatement.setNull(16, 1);
        preparedStatement.setNull(17, 1, "test");
        preparedStatement.setBigDecimal(18, new BigDecimal(10000));
        preparedStatement.setBlob(19, inputStream);
        preparedStatement.setBlob(20, inputStream, 1000000L);
        preparedStatement.setClob(21, clob);
        preparedStatement.setClob(22, reader);
        preparedStatement.setClob(23, reader, 100L);
        preparedStatement.setNString(24, "test");
        preparedStatement.setNCharacterStream(25, reader);
        preparedStatement.setNCharacterStream(26, reader, 1);
        preparedStatement.setNClob(27, nClob);
        preparedStatement.setNClob(28, reader, 1);
        preparedStatement.setObject(29, new Object());
        preparedStatement.setObject(30, new Object(), 1);
        preparedStatement.setObject(31, new Object(), 1, 1);
        preparedStatement.setRef(32, ref);
        preparedStatement.setRowId(33, rowId);
        preparedStatement.setSQLXML(34, sqlxml);
        preparedStatement.setTime(35, new Time(System.currentTimeMillis()));
        preparedStatement.setTimestamp(36, new Timestamp(System.currentTimeMillis()));
        preparedStatement.setTimestamp(37, new Timestamp(System.currentTimeMillis()), Calendar.getInstance());
        preparedStatement.setURL(38, new URL("http", "127.0.0.1", "test"));
        preparedStatement.setBinaryStream(39, inputStream);
        preparedStatement.setBinaryStream(40, inputStream, 1);
        preparedStatement.setBinaryStream(41, inputStream, 1L);
        preparedStatement.setNClob(42, reader);
        preparedStatement.setTime(43, new Time(System.currentTimeMillis()), Calendar.getInstance());
        preparedStatement.setArray(45, array);
        preparedStatement.setBlob(46, blob);
        preparedStatement.setDate(47, new Date(System.currentTimeMillis()), Calendar.getInstance());

        ResultSet resultSet = preparedStatement.executeQuery();
        preparedStatement.close();

        verify(mysqlPreparedStatement).clearParameters();
        verify(mysqlPreparedStatement).executeQuery();
        verify(mysqlPreparedStatement).close();
        verify(mysqlPreparedStatement).setAsciiStream(anyInt(), any(InputStream.class));
        verify(mysqlPreparedStatement).setAsciiStream(anyInt(), any(InputStream.class), anyInt());
        verify(mysqlPreparedStatement).setAsciiStream(anyInt(), any(InputStream.class), anyLong());
        verify(mysqlPreparedStatement).setCharacterStream(anyInt(), any(Reader.class));
        verify(mysqlPreparedStatement).setCharacterStream(anyInt(), any(Reader.class), anyInt());
        verify(mysqlPreparedStatement).setCharacterStream(anyInt(), any(Reader.class), anyLong());
        verify(mysqlPreparedStatement).setShort(anyInt(), anyShort());
        verify(mysqlPreparedStatement).setInt(anyInt(), anyInt());
        verify(mysqlPreparedStatement).setString(anyInt(), anyString());
        verify(mysqlPreparedStatement).setBoolean(anyInt(), anyBoolean());
        verify(mysqlPreparedStatement).setLong(anyInt(), anyLong());
        verify(mysqlPreparedStatement).setDouble(anyInt(), anyDouble());
        verify(mysqlPreparedStatement).setFloat(anyInt(), anyFloat());
        verify(mysqlPreparedStatement).setByte(anyInt(), anyByte());
        verify(mysqlPreparedStatement).setBytes(14, bytesParam);
        verify(mysqlPreparedStatement).setDate(anyInt(), any(Date.class));
        verify(mysqlPreparedStatement).setNull(anyInt(), anyInt());
        verify(mysqlPreparedStatement).setNull(anyInt(), anyInt(), anyString());
        verify(mysqlPreparedStatement).setBigDecimal(anyInt(), any(BigDecimal.class));
        verify(mysqlPreparedStatement).setBlob(anyInt(), any(InputStream.class));
        verify(mysqlPreparedStatement).setBlob(anyInt(), any(InputStream.class), anyLong());
        verify(mysqlPreparedStatement).setClob(anyInt(), any(Clob.class));
        verify(mysqlPreparedStatement).setClob(anyInt(), any(Reader.class));
        verify(mysqlPreparedStatement).setClob(anyInt(), any(Reader.class), anyLong());
        verify(mysqlPreparedStatement).setNString(anyInt(), anyString());
        verify(mysqlPreparedStatement).setNCharacterStream(anyInt(), any(Reader.class));
        verify(mysqlPreparedStatement).setNCharacterStream(anyInt(), any(Reader.class), anyLong());
        verify(mysqlPreparedStatement).setNClob(27, nClob);
        verify(mysqlPreparedStatement).setNClob(28, reader, 1);
        verify(mysqlPreparedStatement).setObject(anyInt(), Matchers.anyObject());
        verify(mysqlPreparedStatement).setObject(anyInt(), Matchers.anyObject(), anyInt());
        verify(mysqlPreparedStatement).setObject(anyInt(), Matchers.anyObject(), anyInt(), anyInt());
        verify(mysqlPreparedStatement).setRef(anyInt(), any(Ref.class));
        verify(mysqlPreparedStatement).setRowId(anyInt(), any(RowId.class));
        verify(mysqlPreparedStatement).setSQLXML(anyInt(), any(SQLXML.class));
        verify(mysqlPreparedStatement).setTime(anyInt(), any(Time.class));
        verify(mysqlPreparedStatement).setTimestamp(anyInt(), any(Timestamp.class));
        verify(mysqlPreparedStatement).setTimestamp(anyInt(), any(Timestamp.class), any(Calendar.class));
        verify(mysqlPreparedStatement).setURL(anyInt(), any(URL.class));
        verify(mysqlPreparedStatement).setBinaryStream(anyInt(), any(InputStream.class));
        verify(mysqlPreparedStatement).setBinaryStream(anyInt(), any(InputStream.class), anyInt());
        verify(mysqlPreparedStatement).setBinaryStream(anyInt(), any(InputStream.class), anyLong());
        verify(mysqlPreparedStatement).setNClob(42, reader);
        verify(mysqlPreparedStatement).setTime(anyInt(), any(Time.class), any(Calendar.class));
        verify(mysqlPreparedStatement).setTimestamp(anyInt(), any(Timestamp.class), any(Calendar.class));
        verify(mysqlPreparedStatement).setArray(anyInt(), any(Array.class));
        verify(mysqlPreparedStatement).setBlob(anyInt(), any(Blob.class));
        verify(mysqlPreparedStatement).setDate(anyInt(), any(Date.class), any(Calendar.class));
    }

    @Test
    public void testPreparedStatementConfig() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("INSERT INTO test VALUES( ? , ?)", 1);
        preparedStatement.setInt(1, 1);
        preparedStatement.setString(2, "a");
        preparedStatement.getUpdateCount();
        preparedStatement.setFetchDirection(1);
        preparedStatement.getFetchDirection();
        preparedStatement.getResultSetConcurrency();
        preparedStatement.getResultSetType();
        preparedStatement.isClosed();
        preparedStatement.setPoolable(false);
        preparedStatement.isPoolable();
        preparedStatement.getWarnings();
        preparedStatement.clearWarnings();
        preparedStatement.setCursorName("test");
        preparedStatement.setMaxFieldSize(11);
        preparedStatement.getMaxFieldSize();
        preparedStatement.setMaxRows(10);
        preparedStatement.getMaxRows();
        preparedStatement.getParameterMetaData();
        preparedStatement.setEscapeProcessing(true);
        preparedStatement.setFetchSize(1);
        preparedStatement.getFetchSize();
        preparedStatement.setQueryTimeout(1);
        preparedStatement.getQueryTimeout();
        Connection connection = preparedStatement.getConnection();

        preparedStatement.execute();

        preparedStatement.getMoreResults();
        preparedStatement.getMoreResults(1);
        preparedStatement.getResultSetHoldability();
        preparedStatement.getMetaData();
        preparedStatement.getResultSet();

        preparedStatement.close();
        verify(mysqlPreparedStatement).getUpdateCount();
        verify(mysqlPreparedStatement).getMoreResults();
        verify(mysqlPreparedStatement).setFetchDirection(anyInt());
        verify(mysqlPreparedStatement).getFetchDirection();
        verify(mysqlPreparedStatement).getResultSetType();
        verify(mysqlPreparedStatement).isClosed();
        verify(mysqlPreparedStatement).setPoolable(anyBoolean());
        verify(mysqlPreparedStatement).getWarnings();
        verify(mysqlPreparedStatement).clearWarnings();
        verify(mysqlPreparedStatement).setCursorName(anyString());
        verify(mysqlPreparedStatement).setMaxFieldSize(anyInt());
        verify(mysqlPreparedStatement).getMaxFieldSize();
        verify(mysqlPreparedStatement).setMaxRows(anyInt());
        verify(mysqlPreparedStatement).getMaxRows();
        verify(mysqlPreparedStatement).setEscapeProcessing(anyBoolean());
        verify(mysqlPreparedStatement).getResultSetConcurrency();
        verify(mysqlPreparedStatement).getResultSetConcurrency();
        verify(mysqlPreparedStatement).getResultSetType();
        verify(mysqlPreparedStatement).getMetaData();
        verify(mysqlPreparedStatement).getParameterMetaData();
        verify(mysqlPreparedStatement).getMoreResults(anyInt());
        verify(mysqlPreparedStatement).setFetchSize(anyInt());
        verify(mysqlPreparedStatement).getFetchSize();
        verify(mysqlPreparedStatement).getQueryTimeout();
        verify(mysqlPreparedStatement).setQueryTimeout(anyInt());
        verify(mysqlPreparedStatement).getResultSet();
        assertThat(connection, CoreMatchers.<Connection>is(swConnection));
    }

    @Test
    public void testExecuteQuery() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("SELECT * FROM test", 1, 1, 1);
        ResultSet resultSet = preparedStatement.executeQuery();

        preparedStatement.close();

        verify(mysqlPreparedStatement).executeQuery();
        verify(mysqlPreparedStatement).close();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/executeQuery", "SELECT * FROM test");
    }

    @Test
    public void testExecute() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("SELECT * FROM test", 1, 1, 1);
        preparedStatement.execute();

        preparedStatement.close();

        verify(mysqlPreparedStatement).execute();
        verify(mysqlPreparedStatement).close();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/execute", "SELECT * FROM test");
    }

    @Test
    public void testQuerySqlWithSql() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("SELECT * FROM test", 1);
        ResultSet resultSet = preparedStatement.executeQuery("SELECT * FROM test");

        preparedStatement.getGeneratedKeys();
        preparedStatement.close();

        verify(mysqlPreparedStatement).executeQuery(anyString());
        verify(mysqlPreparedStatement).close();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/executeQuery", "SELECT * FROM test");

    }

    @Test
    public void testInsertWithAutoGeneratedKey() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("INSERT INTO test VALUES(?)", 1);
        boolean insertCount = preparedStatement.execute("INSERT INTO test VALUES(1)", 1);
        preparedStatement.close();

        verify(mysqlPreparedStatement).execute(anyString(), anyInt());
        verify(mysqlPreparedStatement).close();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/execute", "INSERT INTO test VALUES(1)");

    }

    @Test
    public void testInsertWithIntColumnIndexes() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("INSERT INTO test VALUES(?)", 1);
        boolean insertCount = preparedStatement.execute("INSERT INTO test VALUES(1)", new int[] {
            1,
            2
        });
        preparedStatement.close();

        verify(mysqlPreparedStatement).close();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/execute", "INSERT INTO test VALUES(1)");

    }

    @Test
    public void testInsertWithStringColumnIndexes() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("INSERT INTO test VALUES(?)", 1);
        boolean insertCount = preparedStatement.execute("INSERT INTO test VALUES(1)", new String[] {
            "1",
            "2"
        });
        preparedStatement.close();

        verify(mysqlPreparedStatement).close();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/execute", "INSERT INTO test VALUES(1)");

    }

    @Test
    public void testExecuteWithSQL() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("UPDATE test SET  a = ?");
        preparedStatement.setString(1, "a");
        boolean updateCount = preparedStatement.execute("UPDATE test SET  a = 1");
        preparedStatement.cancel();
        preparedStatement.close();

        verify(mysqlPreparedStatement).execute(anyString());
        verify(mysqlPreparedStatement).close();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/execute", "UPDATE test SET  a = 1");

    }

    @Test
    public void testExecuteUpdate() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("UPDATE test SET  a = ?");
        preparedStatement.setString(1, "a");
        int updateCount = preparedStatement.executeUpdate();
        preparedStatement.cancel();
        preparedStatement.close();

        verify(mysqlPreparedStatement).executeUpdate();
        verify(mysqlPreparedStatement).close();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/executeUpdate", "UPDATE test SET  a = ?");

    }

    @Test
    public void testUpdateSql() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("UPDATE test SET  a = ?");

        int updateCount = preparedStatement.executeUpdate("UPDATE test SET  a = 1");
        preparedStatement.cancel();
        preparedStatement.close();

        verify(mysqlPreparedStatement).executeUpdate(anyString());
        verify(mysqlPreparedStatement).close();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/executeUpdate", "UPDATE test SET  a = 1");

    }

    @Test
    public void testUpdateWithAutoGeneratedKey() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("UPDATE test SET  a = ?");

        int updateCount = preparedStatement.executeUpdate("UPDATE test SET  a = 1", 1);
        preparedStatement.cancel();
        preparedStatement.close();

        verify(mysqlPreparedStatement).close();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/executeUpdate", "UPDATE test SET  a = 1");
    }

    @Test
    public void testUpdateWithIntColumnIndexes() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("UPDATE test SET  a = ?");

        int updateCount = preparedStatement.executeUpdate("UPDATE test SET  a = 1", new int[] {1});
        preparedStatement.cancel();
        preparedStatement.close();

        verify(mysqlPreparedStatement).close();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/executeUpdate", "UPDATE test SET  a = 1");

    }

    @Test
    public void testUpdateWithStringColumnIndexes() throws SQLException {
        PreparedStatement preparedStatement = swConnection.prepareStatement("UPDATE test SET  a = ?");

        int updateCount = preparedStatement.executeUpdate("UPDATE test SET  a = 1", new String[] {"1"});
        preparedStatement.cancel();
        preparedStatement.close();

        verify(mysqlPreparedStatement).close();
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/executeUpdate", "UPDATE test SET  a = 1");
    }

    @Test
    public void testBatch() throws SQLException, MalformedURLException {
        PreparedStatement preparedStatement = multiHostConnection.prepareStatement("UPDATE test SET a = ? WHERE b = ?");
        preparedStatement.setShort(1, (short) 12);
        preparedStatement.setTime(2, new Time(System.currentTimeMillis()));
        preparedStatement.addBatch();
        int[] resultSet = preparedStatement.executeBatch();
        preparedStatement.clearBatch();

        verify(mysqlPreparedStatement).executeBatch();
        verify(mysqlPreparedStatement).addBatch();
        verify(mysqlPreparedStatement).clearBatch();

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/executeBatch", "");

    }

    @Test
    public void testQueryWithMultiHost() throws SQLException {
        PreparedStatement preparedStatement = multiHostConnection.prepareStatement("SELECT * FROM test WHERE a = ? or b = ? or c=? or d = ?", 1, 1);
        preparedStatement.setAsciiStream(1, inputStream);
        preparedStatement.setAsciiStream(2, inputStream, 10);
        preparedStatement.setAsciiStream(3, inputStream, 1000000L);
        preparedStatement.setCharacterStream(4, reader);
        ResultSet resultSet = preparedStatement.executeQuery();

        preparedStatement.close();

        verify(mysqlPreparedStatement).executeQuery();
        verify(mysqlPreparedStatement).close();
    }

    @Test(expected = SQLException.class)
    public void testMultiHostWithException() throws SQLException {
        when(mysqlPreparedStatement.executeQuery()).thenThrow(new SQLException());
        try {
            PreparedStatement preparedStatement = multiHostConnection.prepareStatement("SELECT * FROM test WHERE a = ? or b = ? or c=? or d = ? or e=?");
            preparedStatement.setBigDecimal(1, new BigDecimal(10000));
            preparedStatement.setBlob(2, inputStream);
            preparedStatement.setBlob(3, inputStream, 1000000L);
            preparedStatement.setByte(3, (byte) 1);
            preparedStatement.setBytes(4, new byte[] {
                1,
                2
            });
            preparedStatement.setLong(5, 100L);

            ResultSet resultSet = preparedStatement.executeQuery();

            preparedStatement.close();
        } finally {
            verify(mysqlPreparedStatement).executeQuery();
            verify(mysqlPreparedStatement, times(0)).close();
            verify(mysqlPreparedStatement).setBigDecimal(anyInt(), any(BigDecimal.class));
            verify(mysqlPreparedStatement).setBlob(anyInt(), any(InputStream.class));
            verify(mysqlPreparedStatement).setBlob(anyInt(), any(InputStream.class), anyLong());
            verify(mysqlPreparedStatement).setByte(anyInt(), anyByte());

            TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
            List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
            assertThat(spans.size(), is(1));
            assertDBSpan(spans.get(0), "Mysql/JDBI/PreparedStatement/executeQuery", "SELECT * FROM test WHERE a = ? or b = ? or c=? or d = ? or e=?");

            List<LogDataEntity> logData = SpanHelper.getLogs(spans.get(0));
            Assert.assertThat(logData.size(), is(1));
            assertThat(logData.size(), is(1));
            assertDBSpanLog(logData.get(0));
        }

    }

}
