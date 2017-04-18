package com.a.eye.skywalking.plugin.jdbc;

import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;

import java.sql.SQLException;

/**
 * {@link CallableStatementTracing} create span with the {@link Span#operationName} start with
 * "JDBC/CallableStatement/"and set {@link ConnectionInfo#dbType} to the {@link Tags#COMPONENT}.
 *
 * Notice: {@link Tags#PEERS} may be is null if database connection url don't contain multiple hosts.
 *
 * @author zhangxin
 */
public class CallableStatementTracing {

    public static <R> R execute(java.sql.CallableStatement realStatement,
        ConnectionInfo connectInfo, String method, String sql, Executable<R> exec)
        throws SQLException {
        try {
            Span span = ContextManager.createSpan(connectInfo.getDBType() + "/JDBI/CallableStatement/" + method);
            Tags.DB_TYPE.set(span, "sql");
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
            Tags.DB_INSTANCE.set(span, connectInfo.getDatabaseName());
            Tags.DB_STATEMENT.set(span, sql);
            Tags.SPAN_LAYER.asDB(span);
            Tags.COMPONENT.set(span, connectInfo.getDBType());
            if (!StringUtil.isEmpty(connectInfo.getHosts())) {
                Tags.PEERS.set(span, connectInfo.getHosts());
            } else {
                Tags.PEER_PORT.set(span, connectInfo.getPort());
                Tags.PEER_HOST.set(span, connectInfo.getHost());
            }
            return exec.exe(realStatement, sql);
        } catch (SQLException e) {
            Span span = ContextManager.activeSpan();
            Tags.ERROR.set(span, true);
            span.log(e);
            throw e;
        } finally {
            ContextManager.stopSpan();
        }
    }

    public interface Executable<R> {
        R exe(java.sql.CallableStatement realConnection, String sql)
            throws SQLException;
    }
}
