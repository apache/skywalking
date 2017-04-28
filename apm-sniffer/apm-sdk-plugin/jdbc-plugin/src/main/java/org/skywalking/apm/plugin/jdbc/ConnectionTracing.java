package org.skywalking.apm.plugin.jdbc;

import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.util.StringUtil;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.tag.Tags;

import java.sql.SQLException;

/**
 * {@link ConnectionTracing} create span with the {@link Span#operationName} start with
 * "JDBC/Connection/"and set {@link ConnectionInfo#dbType} to the {@link Tags#COMPONENT}.
 * <p>
 * Notice: {@link Tags#PEERS} may be is null if database connection url don't contain multiple hosts.
 *
 * @author zhangxin
 */
public class ConnectionTracing {

    public static <R> R execute(java.sql.Connection realConnection,
                                ConnectionInfo connectInfo, String method, String sql, Executable<R> exec)
        throws SQLException {
        try {
            Span span = ContextManager.createSpan(connectInfo.getDBType() + "/JDBI/Connection/" + method);
            Tags.DB_TYPE.set(span, "sql");
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
            Tags.DB_INSTANCE.set(span, connectInfo.getDatabaseName());
            Tags.DB_STATEMENT.set(span, sql);
            Tags.COMPONENT.set(span, connectInfo.getDBType());
            Tags.SPAN_LAYER.asDB(span);
            if (!StringUtil.isEmpty(connectInfo.getHosts())) {
                Tags.PEERS.set(span, connectInfo.getHosts());
            } else {
                Tags.PEER_PORT.set(span, connectInfo.getPort());
                Tags.PEER_HOST.set(span, connectInfo.getHost());
            }
            return exec.exe(realConnection, sql);
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
        R exe(java.sql.Connection realConnection, String sql)
            throws SQLException;
    }
}
