package org.skywalking.apm.plugin.jdbc;

import java.sql.SQLException;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.util.StringUtil;

/**
 * {@link PreparedStatementTracing} create span with the {@link Span#operationName} start with
 * "JDBC/PreparedStatement/"and set {@link ConnectionInfo#dbType} to the {@link Tags#COMPONENT}.
 * <p>
 * Notice: {@link Span#peerHost} may be is null if database connection url don't contain multiple hosts.
 *
 * @author zhangxin
 */
public class PreparedStatementTracing {

    public static <R> R execute(java.sql.PreparedStatement realStatement,
                                ConnectionInfo connectInfo, String method, String sql, Executable<R> exec)
        throws SQLException {
        try {
            AbstractSpan span = ContextManager.createSpan(connectInfo.getDBType() + "/JDBI/PreparedStatement/" + method);
            Tags.DB_TYPE.set(span, "sql");
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
            Tags.DB_INSTANCE.set(span, connectInfo.getDatabaseName());
            Tags.DB_STATEMENT.set(span, sql);
            Tags.COMPONENT.set(span, connectInfo.getDBType());
            if (!StringUtil.isEmpty(connectInfo.getHosts())) {
                span.setPeers(connectInfo.getHosts());
            } else {
                span.setPeerHost(connectInfo.getHost());
                span.setPort(connectInfo.getPort());
            }
            Tags.SPAN_LAYER.asDB(span);
            return exec.exe(realStatement, sql);
        } catch (SQLException e) {
            AbstractSpan span = ContextManager.activeSpan();
            Tags.ERROR.set(span, true);
            span.log(e);
            throw e;
        } finally {
            ContextManager.stopSpan();
        }
    }

    public interface Executable<R> {
        R exe(java.sql.PreparedStatement realConnection, String sql)
            throws SQLException;
    }
}
