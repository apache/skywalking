package com.ai.cloud.skywalking.plugin.jdbc.tracing;

import com.ai.cloud.skywalking.buriedpoint.RPCBuriedPointSender;
import com.ai.cloud.skywalking.buriedpoint.type.JDBCBuriedPointType;
import com.ai.cloud.skywalking.model.Identification;

import java.sql.SQLException;

/**
 * 连接级追踪，用于追踪用于Statement的操作追踪
 *
 * @author wusheng
 */
public class StatementTracing {
    private static RPCBuriedPointSender sender = new RPCBuriedPointSender();

    public static <R> R execute(java.sql.Statement realStatement,
                                String connectInfo, String method, String sql, Executable<R> exec)
            throws SQLException {
        try {
            sender.beforeSend(Identification
                    .newBuilder()
                    .viewPoint(connectInfo)
                    .businessKey(
                            "statement."
                                    + method
                                    + (sql == null || sql.length() == 0 ? ""
                                    : ":" + sql)).spanType(JDBCBuriedPointType.instance()).build());
            return exec.exe(realStatement, sql);
        } catch (SQLException e) {
            sender.handleException(e);
            throw e;
        } finally {
            sender.afterSend();
        }
    }

    public interface Executable<R> {
        public R exe(java.sql.Statement realStatement, String sql)
                throws SQLException;
    }
}
