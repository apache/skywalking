package com.a.eye.skywalking.api.plugin.jdbc;

import com.a.eye.skywalking.api.plugin.jdbc.define.JDBCBuriedPointType;
import com.a.eye.skywalking.invoke.monitor.RPCClientInvokeMonitor;
import com.a.eye.skywalking.model.Identification;

import java.sql.SQLException;

/**
 * 连接级追踪，用于追踪用于Connection的操作追踪
 *
 * @author wusheng
 */
public class CallableStatementTracing {
    private static RPCClientInvokeMonitor rpcClientInvokeMonitor = new RPCClientInvokeMonitor();

    public static <R> R execute(java.sql.CallableStatement realStatement,
                                String connectInfo, String method, String sql, Executable<R> exec)
            throws SQLException {
        try {
            rpcClientInvokeMonitor.beforeInvoke(Identification
                    .newBuilder()
                    .viewPoint(connectInfo)
                    .businessKey(
                            "callableStatement."
                                    + method
                                    + (sql == null || sql.length() == 0 ? ""
                                    : ":" + sql)).spanType(JDBCBuriedPointType.INSTANCE).build());
            return exec.exe(realStatement, sql);
        } catch (SQLException e) {
            rpcClientInvokeMonitor.occurException(e);
            throw e;
        } finally {
            rpcClientInvokeMonitor.afterInvoke();
        }
    }

    public interface Executable<R> {
        public R exe(java.sql.CallableStatement realConnection, String sql)
                throws SQLException;
    }
}
