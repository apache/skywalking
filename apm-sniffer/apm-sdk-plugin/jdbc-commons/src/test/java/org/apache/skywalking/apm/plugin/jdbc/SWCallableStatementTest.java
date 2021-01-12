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
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
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
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;

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
public class SWCallableStatementTest extends AbstractStatementTest {

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
    private com.mysql.cj.jdbc.CallableStatement mysqlCallableStatement;
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
        when(jdbcConnection.prepareCall(anyString())).thenReturn(mysqlCallableStatement);
        when(jdbcConnection.prepareCall(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(mysqlCallableStatement);
        when(jdbcConnection.prepareCall(anyString(), anyInt(), anyInt())).thenReturn(mysqlCallableStatement);
    }

    @Test
    public void testSetParam() throws SQLException, MalformedURLException {
        CallableStatement callableStatement = multiHostConnection.prepareCall("SELECT * FROM test WHERE a = ? OR b = ? OR c=? OR d = ? OR e = ?" + " OR e = ? OR f = ? OR g = ? OR h = ? OR i = ? OR j = ? OR k = ? OR l = ? OR m = ?  OR n = ? OR o = ? OR p = ? " + " OR r = ?  OR s = ? OR t = ?  OR u = ?  OR v = ?  OR w = ?  OR x = ?  OR y = ? OR z = ? OR a1 = ? OR a2 = ? OR a3 = ?" + " OR a4 = ? OR a5 = ? OR a6 = ?  OR a7 = ?  OR a8 = ?  OR a9 = ? OR b1 = ? OR b2 = ? OR b3 = ? OR b4 = ? OR b5 = ?" + " OR b6 = ? OR b7 = ? OR b8  = ? OR b9 = ? OR c1 = ?  OR c2 = ? OR c3 = ?");
        callableStatement.clearParameters();
        callableStatement.setAsciiStream(1, inputStream);
        callableStatement.setAsciiStream(2, inputStream, 10);
        callableStatement.setAsciiStream(3, inputStream, 1000000L);
        callableStatement.setCharacterStream(4, reader);
        callableStatement.setCharacterStream(4, reader, 10);
        callableStatement.setCharacterStream(5, reader, 10L);
        callableStatement.setShort(6, (short) 12);
        callableStatement.setInt(7, 1);
        callableStatement.setString(8, "test");
        callableStatement.setBoolean(9, true);
        callableStatement.setLong(10, 100L);
        callableStatement.setDouble(11, 12.0);
        callableStatement.setFloat(12, 12.0f);
        callableStatement.setByte(13, (byte) 1);
        callableStatement.setBytes(14, bytesParam);
        callableStatement.setDate(15, new Date(System.currentTimeMillis()));
        callableStatement.setNull(16, 1);
        callableStatement.setNull(17, 1, "test");
        callableStatement.setBigDecimal(18, new BigDecimal(10000));
        callableStatement.setBlob(19, inputStream);
        callableStatement.setBlob(20, inputStream, 1000000L);
        callableStatement.setClob(21, clob);
        callableStatement.setClob(22, reader);
        callableStatement.setClob(23, reader, 100L);
        callableStatement.setNString(24, "test");
        callableStatement.setNCharacterStream(25, reader);
        callableStatement.setNCharacterStream(26, reader, 1);
        callableStatement.setNClob(27, nClob);
        callableStatement.setNClob(28, reader, 1);
        callableStatement.setObject(29, new Object());
        callableStatement.setObject(30, new Object(), 1);
        callableStatement.setObject(31, new Object(), 1, 1);
        callableStatement.setRef(32, ref);
        callableStatement.setRowId(33, rowId);
        callableStatement.setSQLXML(34, sqlxml);
        callableStatement.setTime(35, new Time(System.currentTimeMillis()));
        callableStatement.setTimestamp(36, new Timestamp(System.currentTimeMillis()));
        callableStatement.setTimestamp(37, new Timestamp(System.currentTimeMillis()), Calendar.getInstance());
        callableStatement.setURL(38, new URL("http", "127.0.0.1", "test"));
        callableStatement.setBinaryStream(39, inputStream);
        callableStatement.setBinaryStream(40, inputStream, 1);
        callableStatement.setBinaryStream(41, inputStream, 1L);
        callableStatement.setNClob(42, reader);
        callableStatement.setTime(43, new Time(System.currentTimeMillis()), Calendar.getInstance());
        callableStatement.setArray(45, array);
        callableStatement.setBlob(46, blob);
        callableStatement.setDate(47, new Date(System.currentTimeMillis()), Calendar.getInstance());

        callableStatement.getCharacterStream(4);
        callableStatement.getCharacterStream("d");
        callableStatement.getShort(6);
        callableStatement.getShort("g");
        callableStatement.getInt(7);
        callableStatement.getInt("h");
        callableStatement.getString(8);
        callableStatement.getString("i");
        callableStatement.getBoolean(9);
        callableStatement.getBoolean("j");
        callableStatement.getLong(10);
        callableStatement.getLong("k");
        callableStatement.getDouble(11);
        callableStatement.getDouble("l");
        callableStatement.getFloat(12);
        callableStatement.getFloat("m");
        callableStatement.getByte(13);
        callableStatement.getByte("n");
        callableStatement.getBytes(14);
        callableStatement.getBytes("o");
        callableStatement.getDate(15);
        callableStatement.getDate("p");
        callableStatement.getBigDecimal(18);
        callableStatement.getBigDecimal("s");
        callableStatement.getBlob(19);
        callableStatement.getBlob("t");
        callableStatement.getClob(21);
        callableStatement.getClob(21);
        callableStatement.getClob("u");
        callableStatement.getNString(24);
        callableStatement.getNString("y");
        callableStatement.getNCharacterStream(25);
        callableStatement.getNCharacterStream("z");
        callableStatement.getNClob(27);
        callableStatement.getNClob("a1");
        callableStatement.getRef(32);
        callableStatement.getRef("a2");
        callableStatement.getRowId(33);
        callableStatement.getRowId("a7");
        callableStatement.getSQLXML(34);
        callableStatement.getSQLXML("a8");
        callableStatement.getTime(35);
        callableStatement.getTime("a9");
        callableStatement.getTimestamp(36);
        callableStatement.getTimestamp("b1");
        callableStatement.getURL(38);
        callableStatement.getURL("b3");
        callableStatement.getArray(45);
        callableStatement.getArray("c4");
        callableStatement.getDate(15);
        callableStatement.getDate("p");
        callableStatement.getDate(15, Calendar.getInstance());
        callableStatement.getDate("p", Calendar.getInstance());
        callableStatement.getTime("a9");
        callableStatement.getTime("a9", Calendar.getInstance());
        callableStatement.getTime(43);
        callableStatement.getTime(43, Calendar.getInstance());
        callableStatement.getTimestamp("p", Calendar.getInstance());
        callableStatement.getTimestamp(36, Calendar.getInstance());
        callableStatement.getObject(29);
        callableStatement.getObject(29, new HashMap<String, Class<?>>());
        callableStatement.getObject("a4");
        callableStatement.getObject("a4", new HashMap<String, Class<?>>());
        callableStatement.getBigDecimal(18, 1);
        callableStatement.wasNull();

        callableStatement.setAsciiStream("a", inputStream);
        callableStatement.setAsciiStream("b", inputStream, 10);
        callableStatement.setAsciiStream("c", inputStream, 1000000L);
        callableStatement.setCharacterStream("d", reader);
        callableStatement.setCharacterStream("e", reader, 10);
        callableStatement.setCharacterStream("f", reader, 10L);
        callableStatement.setShort("g", (short) 12);
        callableStatement.setInt("h", 1);
        callableStatement.setString("i", "test");
        callableStatement.setBoolean("j", true);
        callableStatement.setLong("k", 100L);
        callableStatement.setDouble("l", 12.0);
        callableStatement.setFloat("m", 12.0f);
        callableStatement.setByte("n", (byte) 1);
        callableStatement.setBytes("o", bytesParam);
        callableStatement.setDate("p", new Date(System.currentTimeMillis()));
        callableStatement.setNull("q", 1);
        callableStatement.setNull("r", 1, "test");
        callableStatement.setBigDecimal("s", new BigDecimal(10000));
        callableStatement.setBlob("t", inputStream);
        callableStatement.setBlob("u", inputStream, 1000000L);
        callableStatement.setClob("v", clob);
        callableStatement.setClob("w", reader);
        callableStatement.setClob("x", reader, 100L);
        callableStatement.setNString("y", "test");
        callableStatement.setNCharacterStream("z", reader);
        callableStatement.setNCharacterStream("a1", reader, 1);
        callableStatement.setNClob("a2", nClob);
        callableStatement.setNClob("a3", reader, 1);
        callableStatement.setObject("a4", new Object());
        callableStatement.setObject("a5", new Object(), 1);
        callableStatement.setObject("a6", new Object(), 1, 1);
        callableStatement.setRowId("a7", rowId);
        callableStatement.setSQLXML("a8", sqlxml);
        callableStatement.setTime("a9", new Time(System.currentTimeMillis()));
        callableStatement.setTimestamp("b1", new Timestamp(System.currentTimeMillis()));
        callableStatement.setTimestamp("b2", new Timestamp(System.currentTimeMillis()), Calendar.getInstance());
        callableStatement.setURL("b3", new URL("http", "127.0.0.1", "test"));
        callableStatement.setBinaryStream("b4", inputStream);
        callableStatement.setBinaryStream("b5", inputStream, 1);
        callableStatement.setBinaryStream("b6", inputStream, 1L);
        callableStatement.setNClob("b7", reader);
        callableStatement.setTime("b8", new Time(System.currentTimeMillis()), Calendar.getInstance());
        callableStatement.setBlob("c1", blob);
        callableStatement.setDate("c2", new Date(System.currentTimeMillis()), Calendar.getInstance());

        callableStatement.registerOutParameter("c4", 1);
        callableStatement.registerOutParameter("c5", 1, 1);
        callableStatement.registerOutParameter("c6", 1, "test");
        callableStatement.registerOutParameter(48, 1);
        callableStatement.registerOutParameter(49, 1, 1);
        callableStatement.registerOutParameter(50, 1, "test");

        ResultSet resultSet = callableStatement.executeQuery();
        callableStatement.close();

        verify(mysqlCallableStatement).clearParameters();
        verify(mysqlCallableStatement).executeQuery();
        verify(mysqlCallableStatement).close();
        verify(mysqlCallableStatement).setAsciiStream(anyInt(), any(InputStream.class));
        verify(mysqlCallableStatement).setAsciiStream(anyInt(), any(InputStream.class), anyInt());
        verify(mysqlCallableStatement).setAsciiStream(anyInt(), any(InputStream.class), anyLong());
        verify(mysqlCallableStatement).setCharacterStream(anyInt(), any(Reader.class));
        verify(mysqlCallableStatement).setCharacterStream(anyInt(), any(Reader.class), anyInt());
        verify(mysqlCallableStatement).setCharacterStream(anyInt(), any(Reader.class), anyLong());
        verify(mysqlCallableStatement).setShort(anyInt(), anyShort());
        verify(mysqlCallableStatement).setInt(anyInt(), anyInt());
        verify(mysqlCallableStatement).setString(anyInt(), anyString());
        verify(mysqlCallableStatement).setBoolean(anyInt(), anyBoolean());
        verify(mysqlCallableStatement).setLong(anyInt(), anyLong());
        verify(mysqlCallableStatement).setDouble(anyInt(), anyDouble());
        verify(mysqlCallableStatement).setFloat(anyInt(), anyFloat());
        verify(mysqlCallableStatement).setByte(anyInt(), anyByte());
        verify(mysqlCallableStatement).setBytes(14, bytesParam);
        verify(mysqlCallableStatement).setDate(anyInt(), any(Date.class));
        verify(mysqlCallableStatement).setNull(anyInt(), anyInt());
        verify(mysqlCallableStatement).setNull(anyInt(), anyInt(), anyString());
        verify(mysqlCallableStatement).setBigDecimal(anyInt(), any(BigDecimal.class));
        verify(mysqlCallableStatement).setBlob(anyInt(), any(InputStream.class));
        verify(mysqlCallableStatement).setBlob(anyInt(), any(InputStream.class), anyLong());
        verify(mysqlCallableStatement).setClob(anyInt(), any(Clob.class));
        verify(mysqlCallableStatement).setClob(anyInt(), any(Reader.class));
        verify(mysqlCallableStatement).setClob(anyInt(), any(Reader.class), anyLong());
        verify(mysqlCallableStatement).setNString(anyInt(), anyString());
        verify(mysqlCallableStatement).setNCharacterStream(anyInt(), any(Reader.class));
        verify(mysqlCallableStatement).setNCharacterStream(anyInt(), any(Reader.class), anyLong());
        verify(mysqlCallableStatement).setNClob(27, nClob);
        verify(mysqlCallableStatement).setNClob(28, reader, 1);
        verify(mysqlCallableStatement).setObject(anyInt(), Matchers.anyObject());
        verify(mysqlCallableStatement).setObject(anyInt(), Matchers.anyObject(), anyInt());
        verify(mysqlCallableStatement).setObject(anyInt(), Matchers.anyObject(), anyInt(), anyInt());
        verify(mysqlCallableStatement).setRef(anyInt(), any(Ref.class));
        verify(mysqlCallableStatement).setRowId(anyInt(), any(RowId.class));
        verify(mysqlCallableStatement).setSQLXML(anyInt(), any(SQLXML.class));
        verify(mysqlCallableStatement).setTime(anyInt(), any(Time.class));
        verify(mysqlCallableStatement).setTimestamp(anyInt(), any(Timestamp.class));
        verify(mysqlCallableStatement).setTimestamp(anyInt(), any(Timestamp.class), any(Calendar.class));
        verify(mysqlCallableStatement).setURL(anyInt(), any(URL.class));
        verify(mysqlCallableStatement).setBinaryStream(anyInt(), any(InputStream.class));
        verify(mysqlCallableStatement).setBinaryStream(anyInt(), any(InputStream.class), anyInt());
        verify(mysqlCallableStatement).setBinaryStream(anyInt(), any(InputStream.class), anyLong());
        verify(mysqlCallableStatement).setNClob(42, reader);
        verify(mysqlCallableStatement).setTime(anyInt(), any(Time.class), any(Calendar.class));
        verify(mysqlCallableStatement).setTimestamp(anyInt(), any(Timestamp.class), any(Calendar.class));
        verify(mysqlCallableStatement).setArray(anyInt(), any(Array.class));
        verify(mysqlCallableStatement).setBlob(anyInt(), any(Blob.class));
        verify(mysqlCallableStatement).setDate(anyInt(), any(Date.class), any(Calendar.class));

        verify(mysqlCallableStatement).clearParameters();
        verify(mysqlCallableStatement).executeQuery();
        verify(mysqlCallableStatement).close();
        verify(mysqlCallableStatement).setAsciiStream(anyString(), any(InputStream.class));
        verify(mysqlCallableStatement).setAsciiStream(anyString(), any(InputStream.class), anyInt());
        verify(mysqlCallableStatement).setAsciiStream(anyString(), any(InputStream.class), anyLong());
        verify(mysqlCallableStatement).setCharacterStream(anyString(), any(Reader.class));
        verify(mysqlCallableStatement).setCharacterStream(anyString(), any(Reader.class), anyInt());
        verify(mysqlCallableStatement).setCharacterStream(anyString(), any(Reader.class), anyLong());
        verify(mysqlCallableStatement).setShort(anyString(), anyShort());
        verify(mysqlCallableStatement).setInt(anyString(), anyInt());
        verify(mysqlCallableStatement).setString(anyString(), anyString());
        verify(mysqlCallableStatement).setBoolean(anyString(), anyBoolean());
        verify(mysqlCallableStatement).setLong(anyString(), anyLong());
        verify(mysqlCallableStatement).setDouble(anyString(), anyDouble());
        verify(mysqlCallableStatement).setFloat(anyString(), anyFloat());
        verify(mysqlCallableStatement).setByte(anyString(), anyByte());
        verify(mysqlCallableStatement).setBytes(14, bytesParam);
        verify(mysqlCallableStatement).setDate(anyString(), any(Date.class));
        verify(mysqlCallableStatement).setNull(anyString(), anyInt());
        verify(mysqlCallableStatement).setNull(anyString(), anyInt(), anyString());
        verify(mysqlCallableStatement).setBigDecimal(anyString(), any(BigDecimal.class));
        verify(mysqlCallableStatement).setBlob(anyString(), any(InputStream.class));
        verify(mysqlCallableStatement).setBlob(anyString(), any(InputStream.class), anyLong());
        verify(mysqlCallableStatement).setClob(anyString(), any(Clob.class));
        verify(mysqlCallableStatement).setClob(anyString(), any(Reader.class));
        verify(mysqlCallableStatement).setClob(anyString(), any(Reader.class), anyLong());
        verify(mysqlCallableStatement).setNString(anyString(), anyString());
        verify(mysqlCallableStatement).setNCharacterStream(anyString(), any(Reader.class));
        verify(mysqlCallableStatement).setNCharacterStream(anyString(), any(Reader.class), anyLong());
        verify(mysqlCallableStatement).setNClob(27, nClob);
        verify(mysqlCallableStatement).setNClob(28, reader, 1);
        verify(mysqlCallableStatement).setObject(anyString(), Matchers.anyObject());
        verify(mysqlCallableStatement).setObject(anyString(), Matchers.anyObject(), anyInt());
        verify(mysqlCallableStatement).setObject(anyString(), Matchers.anyObject(), anyInt(), anyInt());
        verify(mysqlCallableStatement).setRowId(anyString(), any(RowId.class));
        verify(mysqlCallableStatement).setSQLXML(anyString(), any(SQLXML.class));
        verify(mysqlCallableStatement).setTime(anyString(), any(Time.class));
        verify(mysqlCallableStatement).setTimestamp(anyString(), any(Timestamp.class));
        verify(mysqlCallableStatement).setTimestamp(anyString(), any(Timestamp.class), any(Calendar.class));
        verify(mysqlCallableStatement).setURL(anyString(), any(URL.class));
        verify(mysqlCallableStatement).setBinaryStream(anyString(), any(InputStream.class));
        verify(mysqlCallableStatement).setBinaryStream(anyString(), any(InputStream.class), anyInt());
        verify(mysqlCallableStatement).setBinaryStream(anyString(), any(InputStream.class), anyLong());
        verify(mysqlCallableStatement).setNClob(42, reader);
        verify(mysqlCallableStatement).setTime(anyString(), any(Time.class), any(Calendar.class));
        verify(mysqlCallableStatement).setTimestamp(anyString(), any(Timestamp.class), any(Calendar.class));
        verify(mysqlCallableStatement).setBlob(anyString(), any(Blob.class));
        verify(mysqlCallableStatement).setDate(anyString(), any(Date.class), any(Calendar.class));
    }

    @Test
    public void testCallableStatementConfig() throws SQLException {
        CallableStatement callableStatement = swConnection.prepareCall("INSERT INTO test VALUES( ? , ?)", 1, 1);
        callableStatement.setInt(1, 1);
        callableStatement.setString(2, "a");
        callableStatement.getUpdateCount();
        callableStatement.setFetchDirection(1);
        callableStatement.getFetchDirection();
        callableStatement.getResultSetConcurrency();
        callableStatement.getResultSetType();
        callableStatement.isClosed();
        callableStatement.setPoolable(false);
        callableStatement.isPoolable();
        callableStatement.getWarnings();
        callableStatement.clearWarnings();
        callableStatement.setCursorName("test");
        callableStatement.setMaxFieldSize(11);
        callableStatement.getMaxFieldSize();
        callableStatement.setMaxRows(10);
        callableStatement.getMaxRows();
        callableStatement.getParameterMetaData();
        callableStatement.setEscapeProcessing(true);
        callableStatement.setFetchSize(1);
        callableStatement.getFetchSize();
        callableStatement.setQueryTimeout(1);
        callableStatement.getQueryTimeout();
        Connection connection = callableStatement.getConnection();

        callableStatement.execute();

        callableStatement.getMoreResults();
        callableStatement.getMoreResults(1);
        callableStatement.getResultSetHoldability();
        callableStatement.getMetaData();
        callableStatement.getResultSet();

        callableStatement.close();
        verify(mysqlCallableStatement).getUpdateCount();
        verify(mysqlCallableStatement).getMoreResults();
        verify(mysqlCallableStatement).setFetchDirection(anyInt());
        verify(mysqlCallableStatement).getFetchDirection();
        verify(mysqlCallableStatement).getResultSetType();
        verify(mysqlCallableStatement).isClosed();
        verify(mysqlCallableStatement).setPoolable(anyBoolean());
        verify(mysqlCallableStatement).getWarnings();
        verify(mysqlCallableStatement).clearWarnings();
        verify(mysqlCallableStatement).setCursorName(anyString());
        verify(mysqlCallableStatement).setMaxFieldSize(anyInt());
        verify(mysqlCallableStatement).getMaxFieldSize();
        verify(mysqlCallableStatement).setMaxRows(anyInt());
        verify(mysqlCallableStatement).getMaxRows();
        verify(mysqlCallableStatement).setEscapeProcessing(anyBoolean());
        verify(mysqlCallableStatement).getResultSetConcurrency();
        verify(mysqlCallableStatement).getResultSetConcurrency();
        verify(mysqlCallableStatement).getResultSetType();
        verify(mysqlCallableStatement).getMetaData();
        verify(mysqlCallableStatement).getParameterMetaData();
        verify(mysqlCallableStatement).getMoreResults(anyInt());
        verify(mysqlCallableStatement).setFetchSize(anyInt());
        verify(mysqlCallableStatement).getFetchSize();
        verify(mysqlCallableStatement).getQueryTimeout();
        verify(mysqlCallableStatement).setQueryTimeout(anyInt());
        verify(mysqlCallableStatement).getResultSet();
        assertThat(connection, CoreMatchers.<Connection>is(swConnection));
    }

    @Test
    public void testExecuteQuery() throws SQLException {
        CallableStatement callableStatement = swConnection.prepareCall("SELECT * FROM test", 1, 1, 1);
        ResultSet resultSet = callableStatement.executeQuery();

        callableStatement.close();

        verify(mysqlCallableStatement).executeQuery();
        verify(mysqlCallableStatement).close();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/executeQuery", "SELECT * FROM test");
    }

    @Test
    public void testQuerySqlWithSql() throws SQLException {
        CallableStatement preparedStatement = swConnection.prepareCall("SELECT * FROM test", 1, 1);
        ResultSet resultSet = preparedStatement.executeQuery("SELECT * FROM test");

        preparedStatement.getGeneratedKeys();
        preparedStatement.close();

        verify(mysqlCallableStatement).executeQuery(anyString());
        verify(mysqlCallableStatement).close();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/executeQuery", "SELECT * FROM test");
    }

    @Test
    public void testInsertWithAutoGeneratedKey() throws SQLException {
        CallableStatement preparedStatement = swConnection.prepareCall("INSERT INTO test VALUES(?)");
        boolean insertCount = preparedStatement.execute("INSERT INTO test VALUES(1)", 1);
        preparedStatement.close();

        verify(mysqlCallableStatement).execute(anyString(), anyInt());
        verify(mysqlCallableStatement).close();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/execute", "INSERT INTO test VALUES(1)");
    }

    @Test
    public void testInsertWithIntColumnIndexes() throws SQLException {
        CallableStatement preparedStatement = swConnection.prepareCall("INSERT INTO test VALUES(?)");
        boolean insertCount = preparedStatement.execute("INSERT INTO test VALUES(1)", new int[] {
            1,
            2
        });
        preparedStatement.close();

        verify(mysqlCallableStatement).close();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/execute", "INSERT INTO test VALUES(1)");
    }

    @Test
    public void testInsertWithStringColumnIndexes() throws SQLException {
        CallableStatement preparedStatement = swConnection.prepareCall("INSERT INTO test VALUES(?)");
        boolean insertCount = preparedStatement.execute("INSERT INTO test VALUES(1)", new String[] {
            "1",
            "2"
        });
        preparedStatement.close();

        verify(mysqlCallableStatement).close();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/execute", "INSERT INTO test VALUES(1)");
    }

    @Test
    public void testExecute() throws SQLException {
        CallableStatement preparedStatement = swConnection.prepareCall("UPDATE test SET  a = ?");
        preparedStatement.setString(1, "a");
        boolean updateCount = preparedStatement.execute("UPDATE test SET  a = 1");
        preparedStatement.cancel();
        preparedStatement.close();

        verify(mysqlCallableStatement).execute(anyString());
        verify(mysqlCallableStatement).close();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/execute", "UPDATE test SET  a = 1");
    }

    @Test
    public void testExecuteUpdate() throws SQLException {
        CallableStatement preparedStatement = swConnection.prepareCall("UPDATE test SET  a = ?");
        preparedStatement.setString(1, "a");
        int updateCount = preparedStatement.executeUpdate();
        preparedStatement.cancel();
        preparedStatement.close();

        verify(mysqlCallableStatement).executeUpdate();
        verify(mysqlCallableStatement).close();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/executeUpdate", "UPDATE test SET  a = ?");
    }

    @Test
    public void testUpdateSql() throws SQLException {
        CallableStatement preparedStatement = swConnection.prepareCall("UPDATE test SET  a = ?");

        int updateCount = preparedStatement.executeUpdate("UPDATE test SET  a = 1");
        preparedStatement.cancel();
        preparedStatement.close();

        verify(mysqlCallableStatement).executeUpdate(anyString());
        verify(mysqlCallableStatement).close();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/executeUpdate", "UPDATE test SET  a = 1");
    }

    @Test
    public void testUpdateWithAutoGeneratedKey() throws SQLException {
        CallableStatement preparedStatement = swConnection.prepareCall("UPDATE test SET  a = ?");

        int updateCount = preparedStatement.executeUpdate("UPDATE test SET  a = 1", 1);
        preparedStatement.cancel();
        preparedStatement.close();

        verify(mysqlCallableStatement).close();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/executeUpdate", "UPDATE test SET  a = 1");
    }

    @Test
    public void testUpdateWithIntColumnIndexes() throws SQLException {
        CallableStatement preparedStatement = swConnection.prepareCall("UPDATE test SET  a = ?");

        int updateCount = preparedStatement.executeUpdate("UPDATE test SET  a = 1", new int[] {1});
        preparedStatement.cancel();
        preparedStatement.close();

        verify(mysqlCallableStatement).close();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/executeUpdate", "UPDATE test SET  a = 1");
    }

    @Test
    public void testUpdateWithStringColumnIndexes() throws SQLException {
        CallableStatement preparedStatement = swConnection.prepareCall("UPDATE test SET  a = ?");

        int updateCount = preparedStatement.executeUpdate("UPDATE test SET  a = 1", new String[] {"1"});
        preparedStatement.cancel();
        preparedStatement.close();

        verify(mysqlCallableStatement).close();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/executeUpdate", "UPDATE test SET  a = 1");
    }

    @Test
    public void testBatch() throws SQLException, MalformedURLException {
        CallableStatement preparedStatement = multiHostConnection.prepareCall("UPDATE test SET a = ? WHERE b = ?");
        preparedStatement.setShort(1, (short) 12);
        preparedStatement.setTime(2, new Time(System.currentTimeMillis()));
        preparedStatement.addBatch();
        int[] resultSet = preparedStatement.executeBatch();
        preparedStatement.clearBatch();

        verify(mysqlCallableStatement).executeBatch();
        verify(mysqlCallableStatement).addBatch();
        verify(mysqlCallableStatement).clearBatch();

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/executeBatch", "");
    }

    @Test
    public void testQueryWithMultiHost() throws SQLException {
        CallableStatement preparedStatement = multiHostConnection.prepareCall("SELECT * FROM test WHERE a = ? OR b = ? OR c=? OR d = ?", 1, 1);
        preparedStatement.setAsciiStream(1, inputStream);
        preparedStatement.setAsciiStream(2, inputStream, 10);
        preparedStatement.setAsciiStream(3, inputStream, 1000000L);
        preparedStatement.setCharacterStream(4, reader);
        ResultSet resultSet = preparedStatement.executeQuery();

        preparedStatement.close();

        verify(mysqlCallableStatement).executeQuery();
        verify(mysqlCallableStatement).close();
    }

    @Test(expected = SQLException.class)
    public void testMultiHostWithException() throws SQLException {
        when(mysqlCallableStatement.executeQuery()).thenThrow(new SQLException());
        try {
            CallableStatement preparedStatement = multiHostConnection.prepareCall("SELECT * FROM test WHERE a = ? OR b = ? OR c=? OR d = ? OR e=?");
            preparedStatement.setBigDecimal(1, new BigDecimal(10000));
            preparedStatement.setBlob(2, inputStream);
            preparedStatement.setBlob(3, inputStream, 1000000L);
            preparedStatement.setByte(3, (byte) 1);
            preparedStatement.setBytes(4, bytesParam);
            preparedStatement.setLong(5, 100L);

            ResultSet resultSet = preparedStatement.executeQuery();

            preparedStatement.close();
        } finally {
            verify(mysqlCallableStatement).executeQuery();
            verify(mysqlCallableStatement, times(0)).close();
            verify(mysqlCallableStatement).setBigDecimal(anyInt(), any(BigDecimal.class));
            verify(mysqlCallableStatement).setBlob(anyInt(), any(InputStream.class));
            verify(mysqlCallableStatement).setBlob(anyInt(), any(InputStream.class), anyLong());
            verify(mysqlCallableStatement).setByte(anyInt(), anyByte());
            assertThat(segmentStorage.getTraceSegments().size(), is(1));
            TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
            List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
            assertThat(spans.size(), is(1));
            assertDBSpan(spans.get(0), "Mysql/JDBI/CallableStatement/executeQuery", "SELECT * FROM test WHERE a = ? OR b = ? OR c=? OR d = ? OR e=?");
            List<LogDataEntity> logs = SpanHelper.getLogs(spans.get(0));
            Assert.assertThat(logs.size(), is(1));
            assertDBSpanLog(logs.get(0));
        }
    }
}
