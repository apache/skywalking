/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.jdbc.mysql.wrapper;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

public class CallableStatementWrapper extends PreparedStatementWrapper implements CallableStatement {

    @Override public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
        call.registerOutParameter(parameterIndex, sqlType);
    }

    @Override public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {
        call.registerOutParameter(parameterIndex, sqlType, scale);
    }

    @Override public boolean wasNull() throws SQLException {
        return call.wasNull();
    }

    @Override public String getString(int parameterIndex) throws SQLException {
        return call.getString(parameterIndex);
    }

    @Override public boolean getBoolean(int parameterIndex) throws SQLException {
        return call.getBoolean(parameterIndex);
    }

    @Override public byte getByte(int parameterIndex) throws SQLException {
        return call.getByte(parameterIndex);
    }

    @Override public short getShort(int parameterIndex) throws SQLException {
        return call.getShort(parameterIndex);
    }

    @Override public int getInt(int parameterIndex) throws SQLException {
        return call.getInt(parameterIndex);
    }

    @Override public long getLong(int parameterIndex) throws SQLException {
        return call.getLong(parameterIndex);
    }

    @Override public float getFloat(int parameterIndex) throws SQLException {
        return call.getFloat(parameterIndex);
    }

    @Override public double getDouble(int parameterIndex) throws SQLException {
        return call.getDouble(parameterIndex);
    }

    @Override @Deprecated public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
        return call.getBigDecimal(parameterIndex, scale);
    }

    @Override public byte[] getBytes(int parameterIndex) throws SQLException {
        return call.getBytes(parameterIndex);
    }

    @Override public Date getDate(int parameterIndex) throws SQLException {
        return call.getDate(parameterIndex);
    }

    @Override public Time getTime(int parameterIndex) throws SQLException {
        return call.getTime(parameterIndex);
    }

    @Override public Timestamp getTimestamp(int parameterIndex) throws SQLException {
        return call.getTimestamp(parameterIndex);
    }

    @Override public Object getObject(int parameterIndex) throws SQLException {
        return call.getObject(parameterIndex);
    }

    @Override public BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
        return call.getBigDecimal(parameterIndex);
    }

    @Override public Object getObject(int parameterIndex, Map<String, Class<?>> map) throws SQLException {
        return call.getObject(parameterIndex, map);
    }

    @Override public Ref getRef(int parameterIndex) throws SQLException {
        return call.getRef(parameterIndex);
    }

    @Override public Blob getBlob(int parameterIndex) throws SQLException {
        return call.getBlob(parameterIndex);
    }

    @Override public Clob getClob(int parameterIndex) throws SQLException {
        return call.getClob(parameterIndex);
    }

    @Override public Array getArray(int parameterIndex) throws SQLException {
        return call.getArray(parameterIndex);
    }

    @Override public Date getDate(int parameterIndex, Calendar cal) throws SQLException {
        return call.getDate(parameterIndex, cal);
    }

    @Override public Time getTime(int parameterIndex, Calendar cal) throws SQLException {
        return call.getTime(parameterIndex, cal);
    }

    @Override public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
        return call.getTimestamp(parameterIndex, cal);
    }

    @Override public void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException {
        call.registerOutParameter(parameterIndex, sqlType, typeName);
    }

    @Override public void registerOutParameter(String parameterName, int sqlType) throws SQLException {
        call.registerOutParameter(parameterName, sqlType);
    }

    @Override public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException {
        call.registerOutParameter(parameterName, sqlType, scale);
    }

    @Override public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException {
        call.registerOutParameter(parameterName, sqlType, typeName);
    }

    @Override public URL getURL(int parameterIndex) throws SQLException {
        return call.getURL(parameterIndex);
    }

    @Override public void setURL(String parameterName, URL val) throws SQLException {
        call.setURL(parameterName, val);
    }

    @Override public void setNull(String parameterName, int sqlType) throws SQLException {
        call.setNull(parameterName, sqlType);
    }

    @Override public void setBoolean(String parameterName, boolean x) throws SQLException {
        call.setBoolean(parameterName, x);
    }

    @Override public void setByte(String parameterName, byte x) throws SQLException {
        call.setByte(parameterName, x);
    }

    @Override public void setShort(String parameterName, short x) throws SQLException {
        call.setShort(parameterName, x);
    }

    @Override public void setInt(String parameterName, int x) throws SQLException {
        call.setInt(parameterName, x);
    }

    @Override public void setLong(String parameterName, long x) throws SQLException {
        call.setLong(parameterName, x);
    }

    @Override public void setFloat(String parameterName, float x) throws SQLException {
        call.setFloat(parameterName, x);
    }

    @Override public void setDouble(String parameterName, double x) throws SQLException {
        call.setDouble(parameterName, x);
    }

    @Override public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException {
        call.setBigDecimal(parameterName, x);
    }

    @Override public void setString(String parameterName, String x) throws SQLException {
        call.setString(parameterName, x);
    }

    @Override public void setBytes(String parameterName, byte[] x) throws SQLException {
        call.setBytes(parameterName, x);
    }

    @Override public void setDate(String parameterName, Date x) throws SQLException {
        call.setDate(parameterName, x);
    }

    @Override public void setTime(String parameterName, Time x) throws SQLException {
        call.setTime(parameterName, x);
    }

    @Override public void setTimestamp(String parameterName, Timestamp x) throws SQLException {
        call.setTimestamp(parameterName, x);
    }

    @Override public void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException {
        call.setAsciiStream(parameterName, x, length);
    }

    @Override public void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException {
        call.setBinaryStream(parameterName, x, length);
    }

    @Override public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException {
        call.setObject(parameterName, x, targetSqlType, scale);
    }

    @Override public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {
        call.setObject(parameterName, x, targetSqlType);
    }

    @Override public void setObject(String parameterName, Object x) throws SQLException {
        call.setObject(parameterName, x);
    }

    @Override public void setCharacterStream(String parameterName, Reader reader, int length) throws SQLException {
        call.setCharacterStream(parameterName, reader, length);
    }

    @Override public void setDate(String parameterName, Date x, Calendar cal) throws SQLException {
        call.setDate(parameterName, x, cal);
    }

    @Override public void setTime(String parameterName, Time x, Calendar cal) throws SQLException {
        call.setTime(parameterName, x, cal);
    }

    @Override public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException {
        call.setTimestamp(parameterName, x, cal);
    }

    @Override public void setNull(String parameterName, int sqlType, String typeName) throws SQLException {
        call.setNull(parameterName, sqlType, typeName);
    }

    @Override public String getString(String parameterName) throws SQLException {
        return call.getString(parameterName);
    }

    @Override public boolean getBoolean(String parameterName) throws SQLException {
        return call.getBoolean(parameterName);
    }

    @Override public byte getByte(String parameterName) throws SQLException {
        return call.getByte(parameterName);
    }

    @Override public short getShort(String parameterName) throws SQLException {
        return call.getShort(parameterName);
    }

    @Override public int getInt(String parameterName) throws SQLException {
        return call.getInt(parameterName);
    }

    @Override public long getLong(String parameterName) throws SQLException {
        return call.getLong(parameterName);
    }

    @Override public float getFloat(String parameterName) throws SQLException {
        return call.getFloat(parameterName);
    }

    @Override public double getDouble(String parameterName) throws SQLException {
        return call.getDouble(parameterName);
    }

    @Override public byte[] getBytes(String parameterName) throws SQLException {
        return call.getBytes(parameterName);
    }

    @Override public Date getDate(String parameterName) throws SQLException {
        return call.getDate(parameterName);
    }

    @Override public Time getTime(String parameterName) throws SQLException {
        return call.getTime(parameterName);
    }

    @Override public Timestamp getTimestamp(String parameterName) throws SQLException {
        return call.getTimestamp(parameterName);
    }

    @Override public Object getObject(String parameterName) throws SQLException {
        return call.getObject(parameterName);
    }

    @Override public BigDecimal getBigDecimal(String parameterName) throws SQLException {
        return call.getBigDecimal(parameterName);
    }

    @Override public Object getObject(String parameterName, Map<String, Class<?>> map) throws SQLException {
        return call.getObject(parameterName, map);
    }

    @Override public Ref getRef(String parameterName) throws SQLException {
        return call.getRef(parameterName);
    }

    @Override public Blob getBlob(String parameterName) throws SQLException {
        return call.getBlob(parameterName);
    }

    @Override public Clob getClob(String parameterName) throws SQLException {
        return call.getClob(parameterName);
    }

    @Override public Array getArray(String parameterName) throws SQLException {
        return call.getArray(parameterName);
    }

    @Override public Date getDate(String parameterName, Calendar cal) throws SQLException {
        return call.getDate(parameterName, cal);
    }

    @Override public Time getTime(String parameterName, Calendar cal) throws SQLException {
        return call.getTime(parameterName, cal);
    }

    @Override public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException {
        return call.getTimestamp(parameterName, cal);
    }

    @Override public URL getURL(String parameterName) throws SQLException {
        return call.getURL(parameterName);
    }

    @Override public RowId getRowId(int parameterIndex) throws SQLException {
        return call.getRowId(parameterIndex);
    }

    @Override public RowId getRowId(String parameterName) throws SQLException {
        return call.getRowId(parameterName);
    }

    @Override public void setRowId(String parameterName, RowId x) throws SQLException {
        call.setRowId(parameterName, x);
    }

    @Override public void setNString(String parameterName, String value) throws SQLException {
        call.setNString(parameterName, value);
    }

    @Override public void setNCharacterStream(String parameterName, Reader value, long length) throws SQLException {
        call.setNCharacterStream(parameterName, value, length);
    }

    @Override public void setNClob(String parameterName, NClob value) throws SQLException {
        call.setNClob(parameterName, value);
    }

    @Override public void setClob(String parameterName, Reader reader, long length) throws SQLException {
        call.setClob(parameterName, reader, length);
    }

    @Override public void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException {
        call.setBlob(parameterName, inputStream, length);
    }

    @Override public void setNClob(String parameterName, Reader reader, long length) throws SQLException {
        call.setNClob(parameterName, reader, length);
    }

    @Override public NClob getNClob(int parameterIndex) throws SQLException {
        return call.getNClob(parameterIndex);
    }

    @Override public NClob getNClob(String parameterName) throws SQLException {
        return call.getNClob(parameterName);
    }

    @Override public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {
        call.setSQLXML(parameterName, xmlObject);
    }

    @Override public SQLXML getSQLXML(int parameterIndex) throws SQLException {
        return call.getSQLXML(parameterIndex);
    }

    @Override public SQLXML getSQLXML(String parameterName) throws SQLException {
        return call.getSQLXML(parameterName);
    }

    @Override public String getNString(int parameterIndex) throws SQLException {
        return call.getNString(parameterIndex);
    }

    @Override public String getNString(String parameterName) throws SQLException {
        return call.getNString(parameterName);
    }

    @Override public Reader getNCharacterStream(int parameterIndex) throws SQLException {
        return call.getNCharacterStream(parameterIndex);
    }

    @Override public Reader getNCharacterStream(String parameterName) throws SQLException {
        return call.getNCharacterStream(parameterName);
    }

    @Override public Reader getCharacterStream(int parameterIndex) throws SQLException {
        return call.getCharacterStream(parameterIndex);
    }

    @Override public Reader getCharacterStream(String parameterName) throws SQLException {
        return call.getCharacterStream(parameterName);
    }

    @Override public void setBlob(String parameterName, Blob x) throws SQLException {
        call.setBlob(parameterName, x);
    }

    @Override public void setClob(String parameterName, Clob x) throws SQLException {
        call.setClob(parameterName, x);
    }

    @Override public void setAsciiStream(String parameterName, InputStream x, long length) throws SQLException {
        call.setAsciiStream(parameterName, x, length);
    }

    @Override public void setBinaryStream(String parameterName, InputStream x, long length) throws SQLException {
        call.setBinaryStream(parameterName, x, length);
    }

    @Override public void setCharacterStream(String parameterName, Reader reader, long length) throws SQLException {
        call.setCharacterStream(parameterName, reader, length);
    }

    @Override public void setAsciiStream(String parameterName, InputStream x) throws SQLException {
        call.setAsciiStream(parameterName, x);
    }

    @Override public void setBinaryStream(String parameterName, InputStream x) throws SQLException {
        call.setBinaryStream(parameterName, x);
    }

    @Override public void setCharacterStream(String parameterName, Reader reader) throws SQLException {
        call.setCharacterStream(parameterName, reader);
    }

    @Override public void setNCharacterStream(String parameterName, Reader value) throws SQLException {
        call.setNCharacterStream(parameterName, value);
    }

    @Override public void setClob(String parameterName, Reader reader) throws SQLException {
        call.setClob(parameterName, reader);
    }

    @Override public void setBlob(String parameterName, InputStream inputStream) throws SQLException {
        call.setBlob(parameterName, inputStream);
    }

    @Override public void setNClob(String parameterName, Reader reader) throws SQLException {
        call.setNClob(parameterName, reader);
    }

    @Override public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
        return call.getObject(parameterIndex, type);
    }

    @Override public <T> T getObject(String parameterName, Class<T> type) throws SQLException {
        return call.getObject(parameterName, type);
    }

    @Override public void setObject(String parameterName, Object x, SQLType targetSqlType,
        int scaleOrLength) throws SQLException {
        call.setObject(parameterName, x, targetSqlType, scaleOrLength);
    }

    @Override public void setObject(String parameterName, Object x, SQLType targetSqlType) throws SQLException {
        call.setObject(parameterName, x, targetSqlType);
    }

    @Override public void registerOutParameter(int parameterIndex, SQLType sqlType) throws SQLException {
        call.registerOutParameter(parameterIndex, sqlType);
    }

    @Override public void registerOutParameter(int parameterIndex, SQLType sqlType, int scale) throws SQLException {
        call.registerOutParameter(parameterIndex, sqlType, scale);
    }

    @Override
    public void registerOutParameter(int parameterIndex, SQLType sqlType, String typeName) throws SQLException {
        call.registerOutParameter(parameterIndex, sqlType, typeName);
    }

    @Override public void registerOutParameter(String parameterName, SQLType sqlType) throws SQLException {
        call.registerOutParameter(parameterName, sqlType);
    }

    @Override public void registerOutParameter(String parameterName, SQLType sqlType, int scale) throws SQLException {
        call.registerOutParameter(parameterName, sqlType, scale);
    }

    @Override
    public void registerOutParameter(String parameterName, SQLType sqlType, String typeName) throws SQLException {
        call.registerOutParameter(parameterName, sqlType, typeName);
    }

    private final CallableStatement call;
    private final String sql;

    public CallableStatementWrapper(CallableStatement call, ConnectionInfo connectionInfo, String sql) {
        super(call, connectionInfo, sql, "Callable");
        this.call = call;
        this.sql = sql;
    }

}
