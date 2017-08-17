package org.skywalking.apm.plugin.jdbc;

import java.sql.SQLException;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.util.StringUtil;

public class CallableStatementTracing {

    public static <R> R execute(java.sql.CallableStatement realStatement,
        ConnectionInfo connectInfo, String method, String sql, Executable<R> exec)
        throws SQLException {
        try {
            String remotePeer;
            if (!StringUtil.isEmpty(connectInfo.getHosts())) {
                remotePeer = connectInfo.getHosts();
            } else {
                remotePeer = connectInfo.getHost() + ":" + connectInfo.getPort();
            }
            AbstractSpan span = ContextManager.createExitSpan(connectInfo.getDBType() + "/JDBI/CallableStatement/" + method, remotePeer);
            Tags.DB_TYPE.set(span, "sql");
            SpanLayer.asDB(span);
            Tags.DB_INSTANCE.set(span, connectInfo.getDatabaseName());
            Tags.DB_STATEMENT.set(span, sql);
            span.setComponent(connectInfo.getComponent());
            return exec.exe(realStatement, sql);
        } catch (SQLException e) {
            AbstractSpan span = ContextManager.activeSpan();
            span.errorOccurred();
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
