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

import com.mysql.cj.api.exceptions.ExceptionInterceptor;
import com.mysql.cj.api.jdbc.ClientInfoProvider;
import com.mysql.cj.api.jdbc.JdbcConnection;
import com.mysql.cj.api.jdbc.JdbcPropertySet;
import com.mysql.cj.api.jdbc.interceptors.StatementInterceptor;
import com.mysql.cj.api.jdbc.result.ResultSetInternalMethods;
import com.mysql.cj.api.mysqla.io.PacketPayload;
import com.mysql.cj.api.mysqla.result.ColumnDefinition;
import com.mysql.cj.core.ServerVersion;
import com.mysql.cj.jdbc.ServerPreparedStatement;
import com.mysql.cj.jdbc.StatementImpl;
import com.mysql.cj.jdbc.result.CachedResultSetMetaData;
import com.mysql.cj.mysqla.MysqlaSession;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.concurrent.Executor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

public class JdbcConnectionWrapper implements JdbcConnection, EnhancedInstance {

    public JdbcConnectionWrapper(JdbcConnection delegate, ConnectionInfo connectionInfo) {
        this.delegate = delegate;
        this.connectionInfo = connectionInfo;
    }

    public JdbcPropertySet getPropertySet() {
        return delegate.getPropertySet();
    }

    public MysqlaSession getSession() {
        return delegate.getSession();
    }

    public void changeUser(String s, String s1) throws SQLException {
        delegate.changeUser(s, s1);
    }

    @Deprecated public void clearHasTriedMaster() {
        delegate.clearHasTriedMaster();
    }

    public PreparedStatement clientPrepareStatement(String s) throws SQLException {
        return delegate.clientPrepareStatement(s);
    }

    public PreparedStatement clientPrepareStatement(String s, int i) throws SQLException {
        return delegate.clientPrepareStatement(s, i);
    }

    public PreparedStatement clientPrepareStatement(String s, int i, int i1) throws SQLException {
        return delegate.clientPrepareStatement(s, i, i1);
    }

    public PreparedStatement clientPrepareStatement(String s, int[] ints) throws SQLException {
        return delegate.clientPrepareStatement(s, ints);
    }

    public PreparedStatement clientPrepareStatement(String s, int i, int i1, int i2) throws SQLException {
        return delegate.clientPrepareStatement(s, i, i1, i2);
    }

    public PreparedStatement clientPrepareStatement(String s, String[] strings) throws SQLException {
        return delegate.clientPrepareStatement(s, strings);
    }

    public int getActiveStatementCount() {
        return delegate.getActiveStatementCount();
    }

    public long getIdleFor() {
        return delegate.getIdleFor();
    }

    public String getStatementComment() {
        return delegate.getStatementComment();
    }

    @Deprecated public boolean hasTriedMaster() {
        return delegate.hasTriedMaster();
    }

    public boolean isInGlobalTx() {
        return delegate.isInGlobalTx();
    }

    public void setInGlobalTx(boolean b) {
        delegate.setInGlobalTx(b);
    }

    public boolean isMasterConnection() {
        return delegate.isMasterConnection();
    }

    public boolean isNoBackslashEscapesSet() {
        return delegate.isNoBackslashEscapesSet();
    }

    public boolean isSameResource(JdbcConnection connection) {
        return delegate.isSameResource(connection);
    }

    public boolean lowerCaseTableNames() {
        return delegate.lowerCaseTableNames();
    }

    public void ping() throws SQLException {
        delegate.ping();
    }

    public void resetServerState() throws SQLException {
        delegate.resetServerState();
    }

    public PreparedStatement serverPrepareStatement(String s) throws SQLException {
        return delegate.serverPrepareStatement(s);
    }

    public PreparedStatement serverPrepareStatement(String s, int i) throws SQLException {
        return delegate.serverPrepareStatement(s, i);
    }

    public PreparedStatement serverPrepareStatement(String s, int i, int i1) throws SQLException {
        return delegate.serverPrepareStatement(s, i, i1);
    }

    public PreparedStatement serverPrepareStatement(String s, int i, int i1, int i2) throws SQLException {
        return delegate.serverPrepareStatement(s, i, i1, i2);
    }

    public PreparedStatement serverPrepareStatement(String s, int[] ints) throws SQLException {
        return delegate.serverPrepareStatement(s, ints);
    }

    public PreparedStatement serverPrepareStatement(String s, String[] strings) throws SQLException {
        return delegate.serverPrepareStatement(s, strings);
    }

    public void setFailedOver(boolean b) {
        delegate.setFailedOver(b);
    }

    public void setStatementComment(String s) {
        delegate.setStatementComment(s);
    }

    public void shutdownServer() throws SQLException {
        delegate.shutdownServer();
    }

    public void reportQueryTime(long l) {
        delegate.reportQueryTime(l);
    }

    public boolean isAbonormallyLongQuery(long l) {
        return delegate.isAbonormallyLongQuery(l);
    }

    public int getAutoIncrementIncrement() {
        return delegate.getAutoIncrementIncrement();
    }

    public boolean hasSameProperties(JdbcConnection connection) {
        return delegate.hasSameProperties(connection);
    }

    public String getHost() {
        return delegate.getHost();
    }

    public String getHostPortPair() {
        return delegate.getHostPortPair();
    }

    public void setProxy(JdbcConnection connection) {
        delegate.setProxy(connection);
    }

    public boolean isServerLocal() throws SQLException {
        return delegate.isServerLocal();
    }

    public int getSessionMaxRows() {
        return delegate.getSessionMaxRows();
    }

    public void setSessionMaxRows(int i) throws SQLException {
        delegate.setSessionMaxRows(i);
    }

    public void setSchema(String s) throws SQLException {
        delegate.setSchema(s);
    }

    public void abortInternal() throws SQLException {
        delegate.abortInternal();
    }

    public void checkClosed() {
        delegate.checkClosed();
    }

    public boolean isProxySet() {
        return delegate.isProxySet();
    }

    public JdbcConnection duplicate() throws SQLException {
        return delegate.duplicate();
    }

    public ResultSetInternalMethods execSQL(StatementImpl statement,
        String s, int i, PacketPayload payload, boolean b, String s1,
        ColumnDefinition definition) throws SQLException {
        return delegate.execSQL(statement, s, i, payload, b, s1, definition);
    }

    public ResultSetInternalMethods execSQL(StatementImpl statement,
        String s, int i, PacketPayload payload, boolean b, String s1,
        ColumnDefinition definition, boolean b1) throws SQLException {
        return delegate.execSQL(statement, s, i, payload, b, s1, definition, b1);
    }

    public StringBuilder generateConnectionCommentBlock(StringBuilder builder) {
        return delegate.generateConnectionCommentBlock(builder);
    }

    public CachedResultSetMetaData getCachedMetaData(String s) {
        return delegate.getCachedMetaData(s);
    }

    public Timer getCancelTimer() {
        return delegate.getCancelTimer();
    }

    public String getCharacterSetMetadata() {
        return delegate.getCharacterSetMetadata();
    }

    public Statement getMetadataSafeStatement() throws SQLException {
        return delegate.getMetadataSafeStatement();
    }

    public boolean getRequiresEscapingEncoder() {
        return delegate.getRequiresEscapingEncoder();
    }

    public ServerVersion getServerVersion() {
        return delegate.getServerVersion();
    }

    public List<StatementInterceptor> getStatementInterceptorsInstances() {
        return delegate.getStatementInterceptorsInstances();
    }

    public void incrementNumberOfPreparedExecutes() {
        delegate.incrementNumberOfPreparedExecutes();
    }

    public void incrementNumberOfPrepares() {
        delegate.incrementNumberOfPrepares();
    }

    public void incrementNumberOfResultSetsCreated() {
        delegate.incrementNumberOfResultSetsCreated();
    }

    public void initializeResultsMetadataFromCache(String s, CachedResultSetMetaData data,
        ResultSetInternalMethods methods) throws SQLException {
        delegate.initializeResultsMetadataFromCache(s, data, methods);
    }

    public void initializeSafeStatementInterceptors() throws SQLException {
        delegate.initializeSafeStatementInterceptors();
    }

    public boolean isReadInfoMsgEnabled() {
        return delegate.isReadInfoMsgEnabled();
    }

    public boolean isReadOnly(boolean b) throws SQLException {
        return delegate.isReadOnly(b);
    }

    public void pingInternal(boolean b, int i) throws SQLException {
        delegate.pingInternal(b, i);
    }

    public void realClose(boolean b, boolean b1, boolean b2, Throwable throwable) throws SQLException {
        delegate.realClose(b, b1, b2, throwable);
    }

    public void recachePreparedStatement(ServerPreparedStatement statement) throws SQLException {
        delegate.recachePreparedStatement(statement);
    }

    public void decachePreparedStatement(ServerPreparedStatement statement) throws SQLException {
        delegate.decachePreparedStatement(statement);
    }

    public void registerQueryExecutionTime(long l) {
        delegate.registerQueryExecutionTime(l);
    }

    public void registerStatement(com.mysql.cj.api.jdbc.Statement statement) {
        delegate.registerStatement(statement);
    }

    public void reportNumberOfTablesAccessed(int i) {
        delegate.reportNumberOfTablesAccessed(i);
    }

    public void setReadInfoMsgEnabled(boolean b) {
        delegate.setReadInfoMsgEnabled(b);
    }

    public void setReadOnlyInternal(boolean b) throws SQLException {
        delegate.setReadOnlyInternal(b);
    }

    public boolean storesLowerCaseTableName() {
        return delegate.storesLowerCaseTableName();
    }

    public void throwConnectionClosedException() throws SQLException {
        delegate.throwConnectionClosedException();
    }

    public void transactionBegun() throws SQLException {
        delegate.transactionBegun();
    }

    public void transactionCompleted() throws SQLException {
        delegate.transactionCompleted();
    }

    public void unregisterStatement(com.mysql.cj.api.jdbc.Statement statement) {
        delegate.unregisterStatement(statement);
    }

    public void unSafeStatementInterceptors() throws SQLException {
        delegate.unSafeStatementInterceptors();
    }

    public boolean useAnsiQuotedIdentifiers() {
        return delegate.useAnsiQuotedIdentifiers();
    }

    public JdbcConnection getMultiHostSafeProxy() {
        return delegate.getMultiHostSafeProxy();
    }

    public ClientInfoProvider getClientInfoProviderImpl() throws SQLException {
        return delegate.getClientInfoProviderImpl();
    }

    public Statement createStatement() throws SQLException {
        return new StatementWrapper(delegate.createStatement(), connectionInfo);
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return new PreparedStatementWrapper(delegate.prepareStatement(sql), connectionInfo, sql);
    }

    public CallableStatement prepareCall(String sql) throws SQLException {
        return new CallableStatementWrapper(delegate.prepareCall(sql), connectionInfo, sql);
    }

    public String nativeSQL(String sql) throws SQLException {
        return delegate.nativeSQL(sql);
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
        delegate.setAutoCommit(autoCommit);
    }

    public boolean getAutoCommit() throws SQLException {
        return delegate.getAutoCommit();
    }

    public void commit() throws SQLException {
        delegate.commit();
    }

    public void rollback() throws SQLException {
        delegate.rollback();
    }

    public void close() throws SQLException {
        delegate.close();
    }

    public boolean isClosed() throws SQLException {
        return delegate.isClosed();
    }

    public DatabaseMetaData getMetaData() throws SQLException {
        return delegate.getMetaData();
    }

    public void setReadOnly(boolean readOnly) throws SQLException {
        delegate.setReadOnly(readOnly);
    }

    public boolean isReadOnly() throws SQLException {
        return delegate.isReadOnly();
    }

    public void setCatalog(String catalog) throws SQLException {
        delegate.setCatalog(catalog);
    }

    public String getCatalog() throws SQLException {
        return delegate.getCatalog();
    }

    public void setTransactionIsolation(int level) throws SQLException {
        delegate.setTransactionIsolation(level);
    }

    public int getTransactionIsolation() throws SQLException {
        return delegate.getTransactionIsolation();
    }

    public SQLWarning getWarnings() throws SQLException {
        return delegate.getWarnings();
    }

    public void clearWarnings() throws SQLException {
        delegate.clearWarnings();
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return new StatementWrapper(delegate.createStatement(resultSetType, resultSetConcurrency), connectionInfo);
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType,
        int resultSetConcurrency) throws SQLException {
        return new PreparedStatementWrapper(delegate.prepareStatement(sql, resultSetType, resultSetConcurrency), connectionInfo, sql);
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return new CallableStatementWrapper(delegate.prepareCall(sql, resultSetType, resultSetConcurrency), connectionInfo, sql);
    }

    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return delegate.getTypeMap();
    }

    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        delegate.setTypeMap(map);
    }

    public void setHoldability(int holdability) throws SQLException {
        delegate.setHoldability(holdability);
    }

    public int getHoldability() throws SQLException {
        return delegate.getHoldability();
    }

    public Savepoint setSavepoint() throws SQLException {
        return delegate.setSavepoint();
    }

    public Savepoint setSavepoint(String name) throws SQLException {
        return delegate.setSavepoint(name);
    }

    public void rollback(Savepoint savepoint) throws SQLException {
        delegate.rollback(savepoint);
    }

    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        delegate.releaseSavepoint(savepoint);
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency,
        int resultSetHoldability) throws SQLException {
        return new StatementWrapper(delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability), connectionInfo);
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
        int resultSetHoldability) throws SQLException {
        return new PreparedStatementWrapper(delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), connectionInfo, sql);
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
        int resultSetHoldability) throws SQLException {
        return new CallableStatementWrapper(delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), connectionInfo, sql);
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return new PreparedStatementWrapper(delegate.prepareStatement(sql, autoGeneratedKeys), connectionInfo, sql);
    }

    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return new PreparedStatementWrapper(delegate.prepareStatement(sql, columnIndexes), connectionInfo, sql);
    }

    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return new PreparedStatementWrapper(delegate.prepareStatement(sql, columnNames), connectionInfo, sql);
    }

    public Clob createClob() throws SQLException {
        return delegate.createClob();
    }

    public Blob createBlob() throws SQLException {
        return delegate.createBlob();
    }

    public NClob createNClob() throws SQLException {
        return delegate.createNClob();
    }

    public SQLXML createSQLXML() throws SQLException {
        return delegate.createSQLXML();
    }

    public boolean isValid(int timeout) throws SQLException {
        return delegate.isValid(timeout);
    }

    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        delegate.setClientInfo(name, value);
    }

    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        delegate.setClientInfo(properties);
    }

    public String getClientInfo(String name) throws SQLException {
        return delegate.getClientInfo(name);
    }

    public Properties getClientInfo() throws SQLException {
        return delegate.getClientInfo();
    }

    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return delegate.createArrayOf(typeName, elements);
    }

    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return delegate.createStruct(typeName, attributes);
    }

    public String getSchema() throws SQLException {
        return delegate.getSchema();
    }

    public void abort(Executor executor) throws SQLException {
        delegate.abort(executor);
    }

    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        delegate.setNetworkTimeout(executor, milliseconds);
    }

    public int getNetworkTimeout() throws SQLException {
        return delegate.getNetworkTimeout();
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }

    public void createNewIO(boolean b) {
        delegate.createNewIO(b);
    }

    public long getId() {
        return delegate.getId();
    }

    public Properties getProperties() {
        return delegate.getProperties();
    }

    public String getProcessHost() {
        return delegate.getProcessHost();
    }

    public Object getConnectionMutex() {
        return delegate.getConnectionMutex();
    }

    public String getURL() {
        return delegate.getURL();
    }

    public String getUser() {
        return delegate.getUser();
    }

    public ExceptionInterceptor getExceptionInterceptor() {
        return delegate.getExceptionInterceptor();
    }

    private final JdbcConnection delegate;
    private final ConnectionInfo connectionInfo;
    private Object dynamicField;

    @Override
    public Object getSkyWalkingDynamicField() {
        return dynamicField;
    }

    @Override
    public void setSkyWalkingDynamicField(Object value) {
        this.dynamicField = value;
    }
}
