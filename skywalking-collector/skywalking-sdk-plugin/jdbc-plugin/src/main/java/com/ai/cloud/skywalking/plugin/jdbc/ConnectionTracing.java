package com.ai.cloud.skywalking.plugin.jdbc;

import com.ai.cloud.skywalking.tracer.RPCClientTracer;
import com.ai.cloud.skywalking.model.Identification;

import java.sql.SQLException;

/**
 * 连接级追踪，用于追踪用于Connection的操作追踪
 *
 * @author wusheng
 */
public class ConnectionTracing {
    private static RPCClientTracer clientTracer = new RPCClientTracer();

    public static <R> R execute(java.sql.Connection realConnection,
                                String connectInfo, String method, String sql, Executable<R> exec)
            throws SQLException {
        try {
            clientTracer.traceBeforeInvoke(Identification
                    .newBuilder()
                    .viewPoint(connectInfo)
                    .businessKey(
                            "connection."
                                    + method
                                    + (sql == null || sql.length() == 0 ? ""
                                    : ":" + sql)).spanType(JDBCBuriedPointType.instance()).build());
            return exec.exe(realConnection, sql);
        } catch (SQLException e) {
            clientTracer.occurException(e);
            throw e;
        } finally {
            clientTracer.traceAfterInvoke();
        }
    }

    public interface Executable<R> {
        public R exe(java.sql.Connection realConnection, String sql)
                throws SQLException;
    }
}
