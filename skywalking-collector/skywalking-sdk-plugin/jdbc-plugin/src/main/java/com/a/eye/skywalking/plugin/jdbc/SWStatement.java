package com.a.eye.skywalking.plugin.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;


public class SWStatement implements java.sql.Statement {
    private Connection realConnection;
    private java.sql.Statement realStatement;
    private String connectInfo;

    SWStatement(Connection realConnection, java.sql.Statement realStatement, String connectInfo) {
        this.realConnection = realConnection;
        this.realStatement = realStatement;
        this.connectInfo = connectInfo;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return realStatement.unwrap(iface);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return realStatement.isWrapperFor(iface);
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "executeQuery", sql, new StatementTracing.Executable<ResultSet>() {
            public ResultSet exe(java.sql.Statement realStatement, String sql)
                    throws SQLException {
                return realStatement.executeQuery(sql);
            }
        });
    }

    public int executeUpdate(String sql) throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new StatementTracing.Executable<Integer>() {
            public Integer exe(java.sql.Statement realStatement, String sql)
                    throws SQLException {
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
        return StatementTracing.execute(realStatement, connectInfo, "execute", sql, new StatementTracing.Executable<Boolean>() {
            public Boolean exe(java.sql.Statement realStatement, String sql)
                    throws SQLException {
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
        realStatement.addBatch(sql);
    }

    public void clearBatch() throws SQLException {
        realStatement.clearBatch();
    }

    public int[] executeBatch() throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "executeBatch", "", new StatementTracing.Executable<int[]>() {
            public int[] exe(java.sql.Statement realStatement, String sql)
                    throws SQLException {
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

    public int executeUpdate(String sql, final int autoGeneratedKeys)
            throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new StatementTracing.Executable<Integer>() {
            public Integer exe(java.sql.Statement realStatement, String sql)
                    throws SQLException {
                return realStatement.executeUpdate(sql, autoGeneratedKeys);
            }
        });
    }

    public int executeUpdate(String sql, final int[] columnIndexes)
            throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new StatementTracing.Executable<Integer>() {
            public Integer exe(java.sql.Statement realStatement, String sql)
                    throws SQLException {
                return realStatement.executeUpdate(sql, columnIndexes);
            }
        });
    }

    public int executeUpdate(String sql, final String[] columnNames)
            throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql, new StatementTracing.Executable<Integer>() {
            public Integer exe(java.sql.Statement realStatement, String sql)
                    throws SQLException {
                return realStatement.executeUpdate(sql, columnNames);
            }
        });
    }

    public boolean execute(String sql, final int autoGeneratedKeys)
            throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "execute", sql, new StatementTracing.Executable<Boolean>() {
            public Boolean exe(java.sql.Statement realStatement, String sql)
                    throws SQLException {
                return realStatement.execute(sql, autoGeneratedKeys);
            }
        });
    }

    public boolean execute(String sql, final int[] columnIndexes) throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "execute", sql, new StatementTracing.Executable<Boolean>() {
            public Boolean exe(java.sql.Statement realStatement, String sql)
                    throws SQLException {
                return realStatement.execute(sql, columnIndexes);
            }
        });
    }

    public boolean execute(String sql, final String[] columnNames)
            throws SQLException {
        return StatementTracing.execute(realStatement, connectInfo, "execute", sql, new StatementTracing.Executable<Boolean>() {
            public Boolean exe(java.sql.Statement realStatement, String sql)
                    throws SQLException {
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

}
