package com.a.eye.skywalking.plugin.jdbc;

import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.invoke.monitor.RPCClientInvokeMonitor;
import com.a.eye.skywalking.plugin.jdbc.define.JDBCBuriedPointType;

import java.sql.SQLException;

/**
 * 连接级追踪，用于追踪用于Connection的操作追踪
 *
 * @author wusheng
 */
public class ConnectionTracing {
    private static RPCClientInvokeMonitor rpcClientInvokeMonitor = new RPCClientInvokeMonitor();

    public static <R> R execute(java.sql.Connection realConnection,
                                String connectInfo, String method, String sql, Executable<R> exec)
            throws SQLException {
        try {
            rpcClientInvokeMonitor.beforeInvoke(Identification
                    .newBuilder()
                    .viewPoint(connectInfo)
                    .businessKey(
                            "connection."
                                    + method
                                    + (sql == null || sql.length() == 0 ? ""
                                    : ":" + sql)).spanType(JDBCBuriedPointType.INSTANCE).build());
            return exec.exe(realConnection, sql);
        } catch (SQLException e) {
            rpcClientInvokeMonitor.occurException(e);
            throw e;
        } finally {
            rpcClientInvokeMonitor.afterInvoke();
        }
    }

    public interface Executable<R> {
        public R exe(java.sql.Connection realConnection, String sql)
                throws SQLException;
    }
}
