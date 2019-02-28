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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

public class StatementWrapper implements Statement {

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        return TracingUtils.trace(connectionInfo, "executeQuery", sql, stateType, new TracingUtils.Executable<ResultSet>() {
            @Override public ResultSet exe(String sql) throws SQLException {
                return statement.executeQuery(sql);
            }
        });
    }

    @Override public int executeUpdate(String sql) throws SQLException {
        return TracingUtils.trace(connectionInfo, "executeUpdate", sql, stateType, new TracingUtils.Executable<Integer>() {
            @Override public Integer exe(String sql) throws SQLException {
                return statement.executeUpdate(sql);
            }
        });
    }

    @Override public void close() throws SQLException {
        statement.close();
    }

    @Override public int getMaxFieldSize() throws SQLException {
        return statement.getMaxFieldSize();
    }

    @Override public void setMaxFieldSize(int max) throws SQLException {
        statement.setMaxFieldSize(max);
    }

    @Override public int getMaxRows() throws SQLException {
        return statement.getMaxRows();
    }

    @Override public void setMaxRows(int max) throws SQLException {
        statement.setMaxRows(max);
    }

    @Override public void setEscapeProcessing(boolean enable) throws SQLException {
        statement.setEscapeProcessing(enable);
    }

    @Override public int getQueryTimeout() throws SQLException {
        return statement.getQueryTimeout();
    }

    @Override public void setQueryTimeout(int seconds) throws SQLException {
        statement.setQueryTimeout(seconds);
    }

    @Override public void cancel() throws SQLException {
        statement.cancel();
    }

    @Override public SQLWarning getWarnings() throws SQLException {
        return statement.getWarnings();
    }

    @Override public void clearWarnings() throws SQLException {
        statement.clearWarnings();
    }

    @Override public void setCursorName(String name) throws SQLException {
        statement.setCursorName(name);
    }

    @Override public boolean execute(String sql) throws SQLException {
        return TracingUtils.trace(connectionInfo, "execute", sql, stateType, new TracingUtils.Executable<Boolean>() {
            @Override public Boolean exe(String sql) throws SQLException {
                return statement.execute(sql);
            }
        });
    }

    @Override public ResultSet getResultSet() throws SQLException {
        return statement.getResultSet();
    }

    @Override public int getUpdateCount() throws SQLException {
        return statement.getUpdateCount();
    }

    @Override public boolean getMoreResults() throws SQLException {
        return statement.getMoreResults();
    }

    @Override public void setFetchDirection(int direction) throws SQLException {
        statement.setFetchDirection(direction);
    }

    @Override public int getFetchDirection() throws SQLException {
        return statement.getFetchDirection();
    }

    @Override public void setFetchSize(int rows) throws SQLException {
        statement.setFetchSize(rows);
    }

    @Override public int getFetchSize() throws SQLException {
        return statement.getFetchSize();
    }

    @Override public int getResultSetConcurrency() throws SQLException {
        return statement.getResultSetConcurrency();
    }

    @Override public int getResultSetType() throws SQLException {
        return statement.getResultSetType();
    }

    @Override public void addBatch(String sql) throws SQLException {
        statement.addBatch(sql);
    }

    @Override public void clearBatch() throws SQLException {
        statement.clearBatch();
    }

    @Override public int[] executeBatch() throws SQLException {
        return TracingUtils.trace(connectionInfo, "executeBatch", null, stateType, new TracingUtils.Executable<int[]>() {
            @Override public int[] exe(String sql) throws SQLException {
                return statement.executeBatch();
            }
        });
    }

    @Override public Connection getConnection() throws SQLException {
        return statement.getConnection();
    }

    @Override public boolean getMoreResults(int current) throws SQLException {
        return statement.getMoreResults(current);
    }

    @Override public ResultSet getGeneratedKeys() throws SQLException {
        return statement.getGeneratedKeys();
    }

    @Override public int executeUpdate(String sql, final int autoGeneratedKeys) throws SQLException {
        return TracingUtils.trace(connectionInfo, "executeUpdate", sql, stateType, new TracingUtils.Executable<Integer>() {
            @Override public Integer exe(String sql) throws SQLException {
                return statement.executeUpdate(sql, autoGeneratedKeys);
            }
        });
    }

    @Override public int executeUpdate(String sql, final int[] columnIndexes) throws SQLException {
        return TracingUtils.trace(connectionInfo, "executeUpdate", sql, stateType, new TracingUtils.Executable<Integer>() {
            @Override public Integer exe(String sql) throws SQLException {
                return statement.executeUpdate(sql, columnIndexes);
            }
        });
    }

    @Override public int executeUpdate(String sql, final String[] columnNames) throws SQLException {
        return TracingUtils.trace(connectionInfo, "executeUpdate", sql, stateType, new TracingUtils.Executable<Integer>() {
            @Override public Integer exe(String sql) throws SQLException {
                return statement.executeUpdate(sql, columnNames);
            }
        });
    }

    @Override public boolean execute(String sql, final int autoGeneratedKeys) throws SQLException {
        return TracingUtils.trace(connectionInfo, "execute", sql, stateType, new TracingUtils.Executable<Boolean>() {
            @Override public Boolean exe(String sql) throws SQLException {
                return statement.execute(sql, autoGeneratedKeys);
            }
        });
    }

    @Override public boolean execute(String sql, final int[] columnIndexes) throws SQLException {
        return TracingUtils.trace(connectionInfo, "execute", sql, stateType, new TracingUtils.Executable<Boolean>() {
            @Override public Boolean exe(String sql) throws SQLException {
                return statement.execute(sql, columnIndexes);
            }
        });
    }

    @Override public boolean execute(String sql, final String[] columnNames) throws SQLException {
        return TracingUtils.trace(connectionInfo, "execute", sql, stateType, new TracingUtils.Executable<Boolean>() {
            @Override public Boolean exe(String sql) throws SQLException {
                return statement.execute(sql, columnNames);
            }
        });
    }

    @Override public int getResultSetHoldability() throws SQLException {
        return statement.getResultSetHoldability();
    }

    @Override public boolean isClosed() throws SQLException {
        return statement.isClosed();
    }

    @Override public void setPoolable(boolean poolable) throws SQLException {
        statement.setPoolable(poolable);
    }

    @Override public boolean isPoolable() throws SQLException {
        return statement.isPoolable();
    }

    @Override public void closeOnCompletion() throws SQLException {
        statement.closeOnCompletion();
    }

    @Override public boolean isCloseOnCompletion() throws SQLException {
        return statement.isCloseOnCompletion();
    }

    @Override public long getLargeUpdateCount() throws SQLException {
        return statement.getLargeUpdateCount();
    }

    @Override public void setLargeMaxRows(long max) throws SQLException {
        statement.setLargeMaxRows(max);
    }

    @Override public long getLargeMaxRows() throws SQLException {
        return statement.getLargeMaxRows();
    }

    @Override public long[] executeLargeBatch() throws SQLException {
        return statement.executeLargeBatch();
    }

    @Override public long executeLargeUpdate(String sql) throws SQLException {
        return TracingUtils.trace(connectionInfo, "executeLargeUpdate", sql, stateType, new TracingUtils.Executable<Long>() {
            @Override public Long exe(String sql) throws SQLException {
                return statement.executeLargeUpdate(sql);
            }
        });
    }

    @Override public long executeLargeUpdate(String sql, final int autoGeneratedKeys) throws SQLException {
        return TracingUtils.trace(connectionInfo, "executeLargeUpdate", sql, stateType, new TracingUtils.Executable<Long>() {
            @Override public Long exe(String sql) throws SQLException {
                return statement.executeLargeUpdate(sql, autoGeneratedKeys);
            }
        });
    }

    @Override public long executeLargeUpdate(String sql, final int[] columnIndexes) throws SQLException {
        return TracingUtils.trace(connectionInfo, "executeLargeUpdate", sql, stateType, new TracingUtils.Executable<Long>() {
            @Override public Long exe(String sql) throws SQLException {
                return statement.executeLargeUpdate(sql, columnIndexes);
            }
        });
    }

    @Override public long executeLargeUpdate(String sql, final String[] columnNames) throws SQLException {
        return TracingUtils.trace(connectionInfo, "executeLargeUpdate", sql, stateType, new TracingUtils.Executable<Long>() {
            @Override public Long exe(String sql) throws SQLException {
                return statement.executeLargeUpdate(sql, columnNames);
            }
        });
    }

    private final Statement statement;
    protected final ConnectionInfo connectionInfo;
    protected final String stateType;

    public StatementWrapper(Statement statement, ConnectionInfo connectionInfo, String stateType) {
        this.statement = statement;
        this.connectionInfo = connectionInfo;
        this.stateType = stateType;
    }

    public StatementWrapper(Statement statement, ConnectionInfo connectionInfo) {
        this(statement, connectionInfo, "Statement");
    }

    @Override public <T> T unwrap(Class<T> iface) throws SQLException {
        return statement.unwrap(iface);
    }

    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return statement.isWrapperFor(iface);
    }
}
