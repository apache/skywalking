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
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
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

/**
 * {@link SWPreparedStatement} wrapper the {@link PreparedStatement} created by client. and it will interceptor the
 * following methods for trace. 1. {@link #execute()} 2. {@link #execute(String)} 3. {@link #execute(String, int[])} 4.
 * {@link #execute(String, String[])} 5. {@link #execute(String, int)} 6. {@link #executeQuery()} 7. {@link
 * #executeQuery(String)} 8. {@link #executeUpdate()} 9. {@link #executeUpdate(String)} 10. {@link
 * #executeUpdate(String, int[])} 11. {@link #executeUpdate(String, String[])} 12. {@link #executeUpdate(String, int)}
 * 13. {@link #addBatch()} 14. {@link #addBatch(String)} ()}
 */
public class SWPreparedStatement implements PreparedStatement {
    private Connection realConnection;
    private PreparedStatement realStatement;
    private ConnectionInfo connectInfo;
    private String sql;

    public SWPreparedStatement(Connection realConnection, PreparedStatement realStatement, ConnectionInfo connectInfo,
        String sql) {
        this.realConnection = realConnection;
        this.realStatement = realStatement;
        this.connectInfo = connectInfo;
        this.sql = sql;
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeQuery", sql, new PreparedStatementTracing.Executable<ResultSet>() {
            @Override
            public ResultSet exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.executeQuery(sql);
            }
        });
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new PreparedStatementTracing.Executable<Integer>() {
            @Override
            public Integer exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate(sql);
            }
        });
    }

    @Override
    public void close() throws SQLException {
        realStatement.close();
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return realStatement.getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        realStatement.setMaxFieldSize(max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return realStatement.getMaxRows();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        realStatement.setMaxRows(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        realStatement.setEscapeProcessing(enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return realStatement.getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        realStatement.setQueryTimeout(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        realStatement.cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return realStatement.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        realStatement.clearWarnings();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        realStatement.setCursorName(name);
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "execute", sql, new PreparedStatementTracing.Executable<Boolean>() {
            @Override
            public Boolean exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.execute(sql);
            }
        });
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return realStatement.getResultSet();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return realStatement.getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return realStatement.getMoreResults();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        realStatement.setFetchDirection(direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return realStatement.getFetchDirection();
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        realStatement.setFetchSize(rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return realStatement.getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return realStatement.getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return realStatement.getResultSetType();
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        realStatement.addBatch(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        realStatement.clearBatch();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeBatch", "", new PreparedStatementTracing.Executable<int[]>() {
            @Override
            public int[] exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.executeBatch();
            }
        });
    }

    @Override
    public Connection getConnection() throws SQLException {
        return realConnection;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return realStatement.getMoreResults(current);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return realStatement.getGeneratedKeys();
    }

    @Override
    public int executeUpdate(String sql, final int autoGeneratedKeys) throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new PreparedStatementTracing.Executable<Integer>() {
            @Override
            public Integer exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate(sql, autoGeneratedKeys);
            }
        });
    }

    @Override
    public int executeUpdate(String sql, final int[] columnIndexes) throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new PreparedStatementTracing.Executable<Integer>() {
            @Override
            public Integer exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate(sql, columnIndexes);
            }
        });
    }

    @Override
    public int executeUpdate(String sql, final String[] columnNames) throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new PreparedStatementTracing.Executable<Integer>() {
            @Override
            public Integer exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate(sql, columnNames);
            }
        });
    }

    @Override
    public boolean execute(String sql, final int autoGeneratedKeys) throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "execute", sql, new PreparedStatementTracing.Executable<Boolean>() {
            @Override
            public Boolean exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.execute(sql, autoGeneratedKeys);
            }
        });
    }

    @Override
    public boolean execute(String sql, final int[] columnIndexes) throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "execute", sql, new PreparedStatementTracing.Executable<Boolean>() {
            @Override
            public Boolean exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.execute(sql, columnIndexes);
            }
        });
    }

    @Override
    public boolean execute(String sql, final String[] columnNames) throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "execute", sql, new PreparedStatementTracing.Executable<Boolean>() {
            @Override
            public Boolean exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.execute(sql, columnNames);
            }
        });
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return realStatement.getResultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return realStatement.isClosed();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        realStatement.setPoolable(poolable);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return realStatement.isPoolable();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        realStatement.closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return realStatement.isCloseOnCompletion();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return realStatement.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return realStatement.isWrapperFor(iface);
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeQuery", sql, new PreparedStatementTracing.Executable<ResultSet>() {
            @Override
            public ResultSet exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.executeQuery();
            }
        });
    }

    @Override
    public int executeUpdate() throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new PreparedStatementTracing.Executable<Integer>() {
            @Override
            public Integer exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate();
            }
        });
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        realStatement.setNull(parameterIndex, sqlType);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        realStatement.setBoolean(parameterIndex, x);
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        realStatement.setByte(parameterIndex, x);
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        realStatement.setShort(parameterIndex, x);
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        realStatement.setInt(parameterIndex, x);
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        realStatement.setLong(parameterIndex, x);
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        realStatement.setFloat(parameterIndex, x);
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        realStatement.setDouble(parameterIndex, x);
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        realStatement.setBigDecimal(parameterIndex, x);
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        realStatement.setString(parameterIndex, x);
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        realStatement.setBytes(parameterIndex, x);
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        realStatement.setDate(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        realStatement.setTime(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        realStatement.setTimestamp(parameterIndex, x);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        realStatement.setAsciiStream(parameterIndex, x, length);
    }

    @Override
    @Deprecated
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        realStatement.setUnicodeStream(parameterIndex, x, length);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        realStatement.setBinaryStream(parameterIndex, x, length);
    }

    @Override
    public void clearParameters() throws SQLException {
        realStatement.clearParameters();
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        realStatement.setObject(parameterIndex, x, targetSqlType);
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        realStatement.setObject(parameterIndex, x);
    }

    @Override
    public boolean execute() throws SQLException {
        return PreparedStatementTracing.execute(realStatement, connectInfo, "execute", sql, new PreparedStatementTracing.Executable<Boolean>() {
            @Override
            public Boolean exe(PreparedStatement realStatement, String sql) throws SQLException {
                return realStatement.execute();
            }
        });
    }

    @Override
    public void addBatch() throws SQLException {
        realStatement.addBatch();
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        realStatement.setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        realStatement.setRef(parameterIndex, x);
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        realStatement.setBlob(parameterIndex, x);
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        realStatement.setClob(parameterIndex, x);
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        realStatement.setArray(parameterIndex, x);
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return realStatement.getMetaData();
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        realStatement.setDate(parameterIndex, x, cal);
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        realStatement.setTime(parameterIndex, x, cal);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        realStatement.setTimestamp(parameterIndex, x, cal);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        realStatement.setNull(parameterIndex, sqlType, typeName);
    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        realStatement.setURL(parameterIndex, x);
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return realStatement.getParameterMetaData();
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        realStatement.setRowId(parameterIndex, x);
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        realStatement.setNString(parameterIndex, value);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        realStatement.setNCharacterStream(parameterIndex, value, length);
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        realStatement.setNClob(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        realStatement.setClob(parameterIndex, reader, length);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        realStatement.setBlob(parameterIndex, inputStream, length);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        realStatement.setNClob(parameterIndex, reader, length);
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        realStatement.setSQLXML(parameterIndex, xmlObject);
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        realStatement.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        realStatement.setAsciiStream(parameterIndex, x, length);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        realStatement.setBinaryStream(parameterIndex, x, length);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        realStatement.setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        realStatement.setAsciiStream(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        realStatement.setBinaryStream(parameterIndex, x);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        realStatement.setCharacterStream(parameterIndex, reader);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        realStatement.setNCharacterStream(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        realStatement.setClob(parameterIndex, reader);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        realStatement.setBlob(parameterIndex, inputStream);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        realStatement.setNClob(parameterIndex, reader);
    }

}
