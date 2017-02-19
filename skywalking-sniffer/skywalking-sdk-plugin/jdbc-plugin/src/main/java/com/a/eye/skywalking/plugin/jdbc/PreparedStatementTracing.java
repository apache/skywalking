package com.a.eye.skywalking.plugin.jdbc;

import com.a.eye.skywalking.context.ContextManager;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;
import java.sql.SQLException;

/**
 * 连接级追踪，用于追踪用于Connection的操作追踪
 *
 * @author wusheng
 */
public class PreparedStatementTracing {

    public static <R> R execute(java.sql.PreparedStatement realStatement,
        String connectInfo, String method, String sql, Executable<R> exec)
        throws SQLException {
        Span span = ContextManager.INSTANCE.createSpan("JDBC/PreparedStatement/" + method);
        try {
            Tags.SPAN_LAYER.asDBAccess(span);
            Tags.DB_URL.set(span, connectInfo);
            Tags.DB_STATEMENT.set(span, sql);
            return exec.exe(realStatement, sql);
        } catch (SQLException e) {
            span.log(e);
            throw e;
        } finally {
            ContextManager.INSTANCE.stopSpan(span);
        }
    }

    public interface Executable<R> {
        R exe(java.sql.PreparedStatement realConnection, String sql)
            throws SQLException;
    }
}
