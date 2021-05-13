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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;

/**
 * {@link SWStatement} wrapper the {@link java.sql.Statement} created by client. and it will interceptor the following
 * methods for trace. 1. {@link #execute(String)} 2. {@link #execute(String, int[])} 3. {@link #execute(String,
 * String[])} 4. {@link #execute(String, int)} 5. {@link #executeQuery(String)} 6. {@link #executeUpdate(String)} 7.
 * {@link #executeUpdate(String, int[])} 8. {@link #executeUpdate(String, String[])} 9. {@link #executeUpdate(String,
 * int)} 10. {@link #addBatch(String)} ()}
 */

public class SWStatement implements java.sql.Statement {
    private Connection realConnection;
    private java.sql.Statement realStatement;
    private ConnectionInfo connectInfo;

    public SWStatement(Connection realConnection, java.sql.Statement realStatement, ConnectionInfo connectInfo) {
        this.realConnection = realConnection;
        this.realStatement = realStatement;
        this.connectInfo = connectInfo;
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
    public ResultSet executeQuery(String sql) throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "executeQuery", sql, new StatementTracing.Executable<ResultSet>() {
            @Override
            public ResultSet exe(java.sql.Statement realStatement, String sql) throws SQLException {
                return realStatement.executeQuery(sql);
            }
        });
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new StatementTracing.Executable<Integer>() {
            @Override
            public Integer exe(java.sql.Statement realStatement, String sql) throws SQLException {
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
        return StatementTracing.execute(realStatement, connectInfo, "execute", sql, new StatementTracing.Executable<Boolean>() {
            @Override
            public Boolean exe(java.sql.Statement realStatement, String sql) throws SQLException {
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
        return StatementTracing.execute(realStatement, connectInfo, "executeBatch", "", new StatementTracing.Executable<int[]>() {
            @Override
            public int[] exe(java.sql.Statement realStatement, String sql) throws SQLException {
                return realStatement.executeBatch();
            }
        });
    }

    @Override
    public Connection getConnection() throws SQLException {
        return this.realConnection;
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
        return StatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new StatementTracing.Executable<Integer>() {
            @Override
            public Integer exe(java.sql.Statement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate(sql, autoGeneratedKeys);
            }
        });
    }

    @Override
    public int executeUpdate(String sql, final int[] columnIndexes) throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new StatementTracing.Executable<Integer>() {
            @Override
            public Integer exe(java.sql.Statement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate(sql, columnIndexes);
            }
        });
    }

    @Override
    public int executeUpdate(String sql, final String[] columnNames) throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new StatementTracing.Executable<Integer>() {
            @Override
            public Integer exe(java.sql.Statement realStatement, String sql) throws SQLException {
                return realStatement.executeUpdate(sql, columnNames);
            }
        });
    }

    @Override
    public boolean execute(String sql, final int autoGeneratedKeys) throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "execute", sql, new StatementTracing.Executable<Boolean>() {
            @Override
            public Boolean exe(java.sql.Statement realStatement, String sql) throws SQLException {
                return realStatement.execute(sql, autoGeneratedKeys);
            }
        });
    }

    @Override
    public boolean execute(String sql, final int[] columnIndexes) throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "execute", sql, new StatementTracing.Executable<Boolean>() {
            @Override
            public Boolean exe(java.sql.Statement realStatement, String sql) throws SQLException {
                return realStatement.execute(sql, columnIndexes);
            }
        });
    }

    @Override
    public boolean execute(String sql, final String[] columnNames) throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "execute", sql, new StatementTracing.Executable<Boolean>() {
            @Override
            public Boolean exe(java.sql.Statement realStatement, String sql) throws SQLException {
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

}
