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

package org.apache.skywalking.apm.plugin.jdbc.trace;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

/**
 * {@link SWCallableStatement} wrapper the {@link CallableStatement} created by client. and it will interceptor the
 * following methods for trace. 1. {@link #execute()} 2. {@link #execute(String)} 3. {@link #execute(String, int[])} 4.
 * {@link #execute(String, String[])} 5. {@link #execute(String, int)} 6. {@link #executeQuery()} 7. {@link
 * #executeQuery(String)} 8. {@link #executeUpdate()} 9. {@link #executeUpdate(String)} 10. {@link
 * #executeUpdate(String, int[])} 11. {@link #executeUpdate(String, String[])} 12. {@link #executeUpdate(String, int)}
 * 13. {@link #addBatch()} 14. {@link #addBatch(String)} ()}
 */
public class SWCallableStatement implements CallableStatement {
    private Connection realConnection;
    private CallableStatement realStatement;
    private ConnectionInfo connectInfo;
    private String sql;

    public SWCallableStatement(Connection realConnection, CallableStatement realStatement, ConnectionInfo connectInfo,
        String sql) {
        this.realConnection = realConnection;
        this.realStatement = realStatement;
        this.connectInfo = connectInfo;
        this.sql = sql;
    }

    public ResultSet executeQuery() throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "executeQuery", sql, new CallableStatementTracing.Executable<ResultSet>() {
            public ResultSet exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.executeQuery();
            }
        });
    }

    public int executeUpdate() throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new CallableStatementTracing.Executable<Integer>() {
            public Integer exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate();
            }
        });
    }

    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        realStatement.setNull(parameterIndex, sqlType);
    }

    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        realStatement.setBoolean(parameterIndex, x);
    }

    public void setByte(int parameterIndex, byte x) throws SQLException {
        realStatement.setByte(parameterIndex, x);
    }

    public void setShort(int parameterIndex, short x) throws SQLException {
        realStatement.setShort(parameterIndex, x);
    }

    public void setInt(int parameterIndex, int x) throws SQLException {
        realStatement.setInt(parameterIndex, x);
    }

    public void setLong(int parameterIndex, long x) throws SQLException {
        realStatement.setLong(parameterIndex, x);
    }

    public void setFloat(int parameterIndex, float x) throws SQLException {
        realStatement.setFloat(parameterIndex, x);
    }

    public void setDouble(int parameterIndex, double x) throws SQLException {
        realStatement.setDouble(parameterIndex, x);
    }

    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        realStatement.setBigDecimal(parameterIndex, x);
    }

    public void setString(int parameterIndex, String x) throws SQLException {
        realStatement.setString(parameterIndex, x);
    }

    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        realStatement.setBytes(parameterIndex, x);
    }

    public void setDate(int parameterIndex, Date x) throws SQLException {
        realStatement.setDate(parameterIndex, x);
    }

    public void setTime(int parameterIndex, Time x) throws SQLException {
        realStatement.setTime(parameterIndex, x);
    }

    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        realStatement.setTimestamp(parameterIndex, x);
    }

    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        realStatement.setAsciiStream(parameterIndex, x, length);
    }

    @Deprecated
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        realStatement.setUnicodeStream(parameterIndex, x, length);
    }

    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        realStatement.setBinaryStream(parameterIndex, x, length);
    }

    public void clearParameters() throws SQLException {
        realStatement.clearParameters();
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        realStatement.setObject(parameterIndex, x, targetSqlType);
    }

    public void setObject(int parameterIndex, Object x) throws SQLException {
        realStatement.setObject(parameterIndex, x);
    }

    public boolean execute() throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "execute", sql, new CallableStatementTracing.Executable<Boolean>() {
            public Boolean exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.execute();
            }
        });
    }

    public void addBatch() throws SQLException {
        realStatement.addBatch();
    }

    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        realStatement.setCharacterStream(parameterIndex, reader, length);
    }

    public void setRef(int parameterIndex, Ref x) throws SQLException {
        realStatement.setRef(parameterIndex, x);
    }

    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        realStatement.setBlob(parameterIndex, x);
    }

    public void setClob(int parameterIndex, Clob x) throws SQLException {
        realStatement.setClob(parameterIndex, x);
    }

    public void setArray(int parameterIndex, Array x) throws SQLException {
        realStatement.setArray(parameterIndex, x);
    }

    public ResultSetMetaData getMetaData() throws SQLException {
        return realStatement.getMetaData();
    }

    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        realStatement.setDate(parameterIndex, x, cal);
    }

    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        realStatement.setTime(parameterIndex, x, cal);
    }

    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        realStatement.setTimestamp(parameterIndex, x, cal);
    }

    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        realStatement.setNull(parameterIndex, sqlType, typeName);
    }

    public void setURL(int parameterIndex, URL x) throws SQLException {
        realStatement.setURL(parameterIndex, x);
    }

    public ParameterMetaData getParameterMetaData() throws SQLException {
        return realStatement.getParameterMetaData();
    }

    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        realStatement.setRowId(parameterIndex, x);
    }

    public void setNString(int parameterIndex, String value) throws SQLException {
        realStatement.setNString(parameterIndex, value);
    }

    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        realStatement.setNCharacterStream(parameterIndex, value, length);
    }

    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        realStatement.setNClob(parameterIndex, value);
    }

    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        realStatement.setClob(parameterIndex, reader, length);
    }

    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        realStatement.setBlob(parameterIndex, inputStream, length);
    }

    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        realStatement.setNClob(parameterIndex, reader, length);
    }

    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        realStatement.setSQLXML(parameterIndex, xmlObject);
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        realStatement.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }

    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        realStatement.setAsciiStream(parameterIndex, x, length);
    }

    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        realStatement.setBinaryStream(parameterIndex, x, length);
    }

    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        realStatement.setCharacterStream(parameterIndex, reader, length);
    }

    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        realStatement.setAsciiStream(parameterIndex, x);
    }

    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        realStatement.setBinaryStream(parameterIndex, x);
    }

    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        realStatement.setCharacterStream(parameterIndex, reader);
    }

    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        realStatement.setNCharacterStream(parameterIndex, value);
    }

    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        realStatement.setClob(parameterIndex, reader);
    }

    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        realStatement.setBlob(parameterIndex, inputStream);
    }

    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        realStatement.setNClob(parameterIndex, reader);
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "executeQuery", sql, new CallableStatementTracing.Executable<ResultSet>() {
            public ResultSet exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.executeQuery(sql);
            }
        });
    }

    public int executeUpdate(String sql) throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new CallableStatementTracing.Executable<Integer>() {
            public Integer exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate(sql);
            }
        });
    }

    public void close() throws SQLException {
        realStatement.close();
    }

    public int getMaxFieldSize() throws SQLException {
        return realStatement.getMaxFieldSize();
    }

    public void setMaxFieldSize(int max) throws SQLException {
        realStatement.setMaxFieldSize(max);
    }

    public int getMaxRows() throws SQLException {
        return realStatement.getMaxRows();
    }

    public void setMaxRows(int max) throws SQLException {
        realStatement.setMaxRows(max);
    }

    public void setEscapeProcessing(boolean enable) throws SQLException {
        realStatement.setEscapeProcessing(enable);
    }

    public int getQueryTimeout() throws SQLException {
        return realStatement.getQueryTimeout();
    }

    public void setQueryTimeout(int seconds) throws SQLException {
        realStatement.setQueryTimeout(seconds);
    }

    public void cancel() throws SQLException {
        realStatement.cancel();
    }

    public SQLWarning getWarnings() throws SQLException {
        return realStatement.getWarnings();
    }

    public void clearWarnings() throws SQLException {
        realStatement.clearWarnings();
    }

    public void setCursorName(String name) throws SQLException {
        realStatement.setCursorName(name);
    }

    public boolean execute(String sql) throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "execute", sql, new CallableStatementTracing.Executable<Boolean>() {
            public Boolean exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.execute(sql);
            }
        });
    }

    public ResultSet getResultSet() throws SQLException {
        return realStatement.getResultSet();
    }

    public int getUpdateCount() throws SQLException {
        return realStatement.getUpdateCount();
    }

    public boolean getMoreResults() throws SQLException {
        return realStatement.getMoreResults();
    }

    public void setFetchDirection(int direction) throws SQLException {
        realStatement.setFetchDirection(direction);
    }

    public int getFetchDirection() throws SQLException {
        return realStatement.getFetchDirection();
    }

    public void setFetchSize(int rows) throws SQLException {
        realStatement.setFetchSize(rows);
    }

    public int getFetchSize() throws SQLException {
        return realStatement.getFetchSize();
    }

    public int getResultSetConcurrency() throws SQLException {
        return realStatement.getResultSetConcurrency();
    }

    public int getResultSetType() throws SQLException {
        return realStatement.getResultSetType();
    }

    public void addBatch(String sql) throws SQLException {
        realStatement.addBatch();
    }

    public void clearBatch() throws SQLException {
        realStatement.clearBatch();
    }

    public int[] executeBatch() throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "executeBatch", "", new CallableStatementTracing.Executable<int[]>() {
            public int[] exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.executeBatch();
            }
        });
    }

    public Connection getConnection() throws SQLException {
        return this.realConnection;
    }

    public boolean getMoreResults(int current) throws SQLException {
        return realStatement.getMoreResults(current);
    }

    public ResultSet getGeneratedKeys() throws SQLException {
        return realStatement.getGeneratedKeys();
    }

    public int executeUpdate(String sql, final int autoGeneratedKeys) throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new CallableStatementTracing.Executable<Integer>() {
            public Integer exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate(sql, autoGeneratedKeys);
            }
        });
    }

    public int executeUpdate(String sql, final int[] columnIndexes) throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new CallableStatementTracing.Executable<Integer>() {
            public Integer exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate(sql, columnIndexes);
            }
        });
    }

    public int executeUpdate(String sql, final String[] columnNames) throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new CallableStatementTracing.Executable<Integer>() {
            public Integer exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate(sql, columnNames);
            }
        });
    }

    public boolean execute(String sql, final int autoGeneratedKeys) throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "execute", sql, new CallableStatementTracing.Executable<Boolean>() {
            public Boolean exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.execute(sql, autoGeneratedKeys);
            }
        });
    }

    public boolean execute(String sql, final int[] columnIndexes) throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "execute", sql, new CallableStatementTracing.Executable<Boolean>() {
            public Boolean exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.execute(sql, columnIndexes);
            }
        });
    }

    public boolean execute(String sql, final String[] columnNames) throws SQLException {
        return CallableStatementTracing.execute(realStatement, connectInfo, "execute", sql, new CallableStatementTracing.Executable<Boolean>() {
            public Boolean exe(CallableStatement realStatement, String sql) throws SQLException {
                return realStatement.execute(sql, columnNames);
            }
        });
    }

    public int getResultSetHoldability() throws SQLException {
        return realStatement.getResultSetHoldability();
    }

    public boolean isClosed() throws SQLException {
        return realStatement.isClosed();
    }

    public void setPoolable(boolean poolable) throws SQLException {
        realStatement.setPoolable(poolable);
    }

    public boolean isPoolable() throws SQLException {
        return realStatement.isPoolable();
    }

    public void closeOnCompletion() throws SQLException {
        realStatement.closeOnCompletion();
    }

    public boolean isCloseOnCompletion() throws SQLException {
        return realStatement.isCloseOnCompletion();
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return realStatement.unwrap(iface);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return realStatement.isWrapperFor(iface);
    }

    public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
        realStatement.registerOutParameter(parameterIndex, sqlType);
    }

    public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {
        realStatement.registerOutParameter(parameterIndex, sqlType, scale);
    }

    public boolean wasNull() throws SQLException {
        return realStatement.wasNull();
    }

    public String getString(int parameterIndex) throws SQLException {
        return realStatement.getString(parameterIndex);
    }

    public boolean getBoolean(int parameterIndex) throws SQLException {
        return realStatement.getBoolean(parameterIndex);
    }

    public byte getByte(int parameterIndex) throws SQLException {
        return realStatement.getByte(parameterIndex);
    }

    public short getShort(int parameterIndex) throws SQLException {
        return realStatement.getShort(parameterIndex);
    }

    public int getInt(int parameterIndex) throws SQLException {
        return realStatement.getInt(parameterIndex);
    }

    public long getLong(int parameterIndex) throws SQLException {
        return realStatement.getLong(parameterIndex);
    }

    public float getFloat(int parameterIndex) throws SQLException {
        return realStatement.getFloat(parameterIndex);
    }

    public double getDouble(int parameterIndex) throws SQLException {
        return realStatement.getDouble(parameterIndex);
    }

    @Deprecated
    public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
        return realStatement.getBigDecimal(parameterIndex, scale);
    }

    public byte[] getBytes(int parameterIndex) throws SQLException {
        return realStatement.getBytes(parameterIndex);
    }

    public Date getDate(int parameterIndex) throws SQLException {
        return realStatement.getDate(parameterIndex);
    }

    public Time getTime(int parameterIndex) throws SQLException {
        return realStatement.getTime(parameterIndex);
    }

    public Timestamp getTimestamp(int parameterIndex) throws SQLException {
        return realStatement.getTimestamp(parameterIndex);
    }

    public Object getObject(int parameterIndex) throws SQLException {
        return realStatement.getObject(parameterIndex);
    }

    public BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
        return realStatement.getBigDecimal(parameterIndex);
    }

    public Object getObject(int parameterIndex, Map<String, Class<?>> map) throws SQLException {
        return realStatement.getObject(parameterIndex, map);
    }

    public Ref getRef(int parameterIndex) throws SQLException {
        return realStatement.getRef(parameterIndex);
    }

    public Blob getBlob(int parameterIndex) throws SQLException {
        return realStatement.getBlob(parameterIndex);
    }

    public Clob getClob(int parameterIndex) throws SQLException {
        return realStatement.getClob(parameterIndex);
    }

    public Array getArray(int parameterIndex) throws SQLException {
        return realStatement.getArray(parameterIndex);
    }

    public Date getDate(int parameterIndex, Calendar cal) throws SQLException {
        return realStatement.getDate(parameterIndex, cal);
    }

    public Time getTime(int parameterIndex, Calendar cal) throws SQLException {
        return realStatement.getTime(parameterIndex, cal);
    }

    public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
        return realStatement.getTimestamp(parameterIndex, cal);
    }

    public void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException {
        realStatement.registerOutParameter(parameterIndex, sqlType, typeName);
    }

    public void registerOutParameter(String parameterName, int sqlType) throws SQLException {
        realStatement.registerOutParameter(parameterName, sqlType);
    }

    public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException {
        realStatement.registerOutParameter(parameterName, sqlType, scale);
    }

    public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException {
        realStatement.registerOutParameter(parameterName, sqlType, typeName);
    }

    public URL getURL(int parameterIndex) throws SQLException {
        return realStatement.getURL(parameterIndex);
    }

    public void setURL(String parameterName, URL val) throws SQLException {
        realStatement.setURL(parameterName, val);
    }

    public void setNull(String parameterName, int sqlType) throws SQLException {
        realStatement.setNull(parameterName, sqlType);
    }

    public void setBoolean(String parameterName, boolean x) throws SQLException {
        realStatement.setBoolean(parameterName, x);
    }

    public void setByte(String parameterName, byte x) throws SQLException {
        realStatement.setByte(parameterName, x);
    }

    public void setShort(String parameterName, short x) throws SQLException {
        realStatement.setShort(parameterName, x);
    }

    public void setInt(String parameterName, int x) throws SQLException {
        realStatement.setInt(parameterName, x);
    }

    public void setLong(String parameterName, long x) throws SQLException {
        realStatement.setLong(parameterName, x);
    }

    public void setFloat(String parameterName, float x) throws SQLException {
        realStatement.setFloat(parameterName, x);
    }

    public void setDouble(String parameterName, double x) throws SQLException {
        realStatement.setDouble(parameterName, x);
    }

    public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException {
        realStatement.setBigDecimal(parameterName, x);
    }

    public void setString(String parameterName, String x) throws SQLException {
        realStatement.setString(parameterName, x);
    }

    public void setBytes(String parameterName, byte[] x) throws SQLException {
        realStatement.setBytes(parameterName, x);
    }

    public void setDate(String parameterName, Date x) throws SQLException {
        realStatement.setDate(parameterName, x);
    }

    public void setTime(String parameterName, Time x) throws SQLException {
        realStatement.setTime(parameterName, x);
    }

    public void setTimestamp(String parameterName, Timestamp x) throws SQLException {
        realStatement.setTimestamp(parameterName, x);
    }

    public void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException {
        realStatement.setAsciiStream(parameterName, x, length);
    }

    public void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException {
        realStatement.setBinaryStream(parameterName, x, length);
    }

    public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException {
        realStatement.setObject(parameterName, x, targetSqlType, scale);
    }

    public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {
        realStatement.setObject(parameterName, x, targetSqlType);
    }

    public void setObject(String parameterName, Object x) throws SQLException {
        realStatement.setObject(parameterName, x);
    }

    public void setCharacterStream(String parameterName, Reader reader, int length) throws SQLException {
        realStatement.setCharacterStream(parameterName, reader, length);
    }

    public void setDate(String parameterName, Date x, Calendar cal) throws SQLException {
        realStatement.setDate(parameterName, x, cal);
    }

    public void setTime(String parameterName, Time x, Calendar cal) throws SQLException {
        realStatement.setTime(parameterName, x, cal);
    }

    public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException {
        realStatement.setTimestamp(parameterName, x, cal);
    }

    public void setNull(String parameterName, int sqlType, String typeName) throws SQLException {
        realStatement.setNull(parameterName, sqlType, typeName);
    }

    public String getString(String parameterName) throws SQLException {
        return realStatement.getString(parameterName);
    }

    public boolean getBoolean(String parameterName) throws SQLException {
        return realStatement.getBoolean(parameterName);
    }

    public byte getByte(String parameterName) throws SQLException {
        return realStatement.getByte(parameterName);
    }

    public short getShort(String parameterName) throws SQLException {
        return realStatement.getShort(parameterName);
    }

    public int getInt(String parameterName) throws SQLException {
        return realStatement.getInt(parameterName);
    }

    public long getLong(String parameterName) throws SQLException {
        return realStatement.getLong(parameterName);
    }

    public float getFloat(String parameterName) throws SQLException {
        return realStatement.getFloat(parameterName);
    }

    public double getDouble(String parameterName) throws SQLException {
        return realStatement.getDouble(parameterName);
    }

    public byte[] getBytes(String parameterName) throws SQLException {
        return realStatement.getBytes(parameterName);
    }

    public Date getDate(String parameterName) throws SQLException {
        return realStatement.getDate(parameterName);
    }

    public Time getTime(String parameterName) throws SQLException {
        return realStatement.getTime(parameterName);
    }

    public Timestamp getTimestamp(String parameterName) throws SQLException {
        return realStatement.getTimestamp(parameterName);
    }

    public Object getObject(String parameterName) throws SQLException {
        return realStatement.getObject(parameterName);
    }

    public BigDecimal getBigDecimal(String parameterName) throws SQLException {
        return realStatement.getBigDecimal(parameterName);
    }

    public Object getObject(String parameterName, Map<String, Class<?>> map) throws SQLException {
        return realStatement.getObject(parameterName, map);
    }

    public Ref getRef(String parameterName) throws SQLException {
        return realStatement.getRef(parameterName);
    }

    public Blob getBlob(String parameterName) throws SQLException {
        return realStatement.getBlob(parameterName);
    }

    public Clob getClob(String parameterName) throws SQLException {
        return realStatement.getClob(parameterName);
    }

    public Array getArray(String parameterName) throws SQLException {
        return realStatement.getArray(parameterName);
    }

    public Date getDate(String parameterName, Calendar cal) throws SQLException {
        return realStatement.getDate(parameterName, cal);
    }

    public Time getTime(String parameterName, Calendar cal) throws SQLException {
        return realStatement.getTime(parameterName, cal);
    }

    public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException {
        return realStatement.getTimestamp(parameterName, cal);
    }

    public URL getURL(String parameterName) throws SQLException {
        return realStatement.getURL(parameterName);
    }

    public RowId getRowId(int parameterIndex) throws SQLException {
        return realStatement.getRowId(parameterIndex);
    }

    public RowId getRowId(String parameterName) throws SQLException {
        return realStatement.getRowId(parameterName);
    }

    public void setRowId(String parameterName, RowId x) throws SQLException {
        realStatement.setRowId(parameterName, x);
    }

    public void setNString(String parameterName, String value) throws SQLException {
        realStatement.setNString(parameterName, value);
    }

    public void setNCharacterStream(String parameterName, Reader value, long length) throws SQLException {
        realStatement.setNCharacterStream(parameterName, value, length);
    }

    public void setNClob(String parameterName, NClob value) throws SQLException {
        realStatement.setNClob(parameterName, value);
    }

    public void setClob(String parameterName, Reader reader, long length) throws SQLException {
        realStatement.setClob(parameterName, reader, length);
    }

    public void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException {
        realStatement.setBlob(parameterName, inputStream, length);
    }

    public void setNClob(String parameterName, Reader reader, long length) throws SQLException {
        realStatement.setNClob(parameterName, reader, length);
    }

    public NClob getNClob(int parameterIndex) throws SQLException {
        return realStatement.getNClob(parameterIndex);
    }

    public NClob getNClob(String parameterName) throws SQLException {
        return realStatement.getNClob(parameterName);
    }

    public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {
        realStatement.setSQLXML(parameterName, xmlObject);
    }

    public SQLXML getSQLXML(int parameterIndex) throws SQLException {
        return realStatement.getSQLXML(parameterIndex);
    }

    public SQLXML getSQLXML(String parameterName) throws SQLException {
        return realStatement.getSQLXML(parameterName);
    }

    public String getNString(int parameterIndex) throws SQLException {
        return realStatement.getNString(parameterIndex);
    }

    public String getNString(String parameterName) throws SQLException {
        return realStatement.getNString(parameterName);
    }

    public Reader getNCharacterStream(int parameterIndex) throws SQLException {
        return realStatement.getNCharacterStream(parameterIndex);
    }

    public Reader getNCharacterStream(String parameterName) throws SQLException {
        return realStatement.getNCharacterStream(parameterName);
    }

    public Reader getCharacterStream(int parameterIndex) throws SQLException {
        return realStatement.getCharacterStream(parameterIndex);
    }

    public Reader getCharacterStream(String parameterName) throws SQLException {
        return realStatement.getCharacterStream(parameterName);
    }

    public void setBlob(String parameterName, Blob x) throws SQLException {
        realStatement.setBlob(parameterName, x);
    }

    public void setClob(String parameterName, Clob x) throws SQLException {
        realStatement.setClob(parameterName, x);
    }

    public void setAsciiStream(String parameterName, InputStream x, long length) throws SQLException {
        realStatement.setAsciiStream(parameterName, x, length);
    }

    public void setBinaryStream(String parameterName, InputStream x, long length) throws SQLException {
        realStatement.setBinaryStream(parameterName, x, length);
    }

    public void setCharacterStream(String parameterName, Reader reader, long length) throws SQLException {
        realStatement.setCharacterStream(parameterName, reader, length);
    }

    public void setAsciiStream(String parameterName, InputStream x) throws SQLException {
        realStatement.setAsciiStream(parameterName, x);
    }

    public void setBinaryStream(String parameterName, InputStream x) throws SQLException {
        realStatement.setBinaryStream(parameterName, x);
    }

    public void setCharacterStream(String parameterName, Reader reader) throws SQLException {
        realStatement.setCharacterStream(parameterName, reader);
    }

    public void setNCharacterStream(String parameterName, Reader value) throws SQLException {
        realStatement.setNCharacterStream(parameterName, value);
    }

    public void setClob(String parameterName, Reader reader) throws SQLException {
        realStatement.setClob(parameterName, reader);
    }

    public void setBlob(String parameterName, InputStream inputStream) throws SQLException {
        realStatement.setBlob(parameterName, inputStream);
    }

    public void setNClob(String parameterName, Reader reader) throws SQLException {
        realStatement.setNClob(parameterName, reader);
    }

    public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
        return realStatement.getObject(parameterIndex, type);
    }

    public <T> T getObject(String parameterName, Class<T> type) throws SQLException {
        return realStatement.getObject(parameterName, type);
    }

}
