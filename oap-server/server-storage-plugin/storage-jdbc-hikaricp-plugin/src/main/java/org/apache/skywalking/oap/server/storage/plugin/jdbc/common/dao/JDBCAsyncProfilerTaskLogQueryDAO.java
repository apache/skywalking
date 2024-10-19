package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskLogRecord;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.core.query.AsyncProfilerTaskLog;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskLogQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class JDBCAsyncProfilerTaskLogQueryDAO implements IAsyncProfilerTaskLogQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<AsyncProfilerTaskLog> getTaskLogList() {
        List<String> tables = tableHelper.getTablesWithinTTL(AsyncProfilerTaskLogRecord.INDEX_NAME);
        final List<AsyncProfilerTaskLog> results = new ArrayList<AsyncProfilerTaskLog>();
        for (String table : tables) {
            SQLAndParameters sqlAndParameters = buildSQL(table);
            List<AsyncProfilerTaskLog> logs = jdbcClient.executeQuery(
                    sqlAndParameters.sql(),
                    resultSet -> {
                        final List<AsyncProfilerTaskLog> tasks = new ArrayList<>();
                        while (resultSet.next()) {
                            tasks.add(parseLog(resultSet));
                        }
                        return tasks;
                    },
                    sqlAndParameters.parameters());
            results.addAll(logs);
        }
        return results;
    }

    private SQLAndParameters buildSQL(String table) {
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>(2);
        sql.append("select * from ").append(table)
                .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
        parameters.add(AsyncProfilerTaskRecord.INDEX_NAME);
        sql.append(" order by ").append(AsyncProfilerTaskLogRecord.OPERATION_TIME).append(" desc");
        return new SQLAndParameters(sql.toString(), parameters);
    }

    private AsyncProfilerTaskLog parseLog(ResultSet data) throws SQLException {
        return AsyncProfilerTaskLog.builder()
                .id(data.getString("id"))
                .taskId(data.getString(AsyncProfilerTaskLogRecord.TASK_ID))
                .instanceId(data.getString(AsyncProfilerTaskLogRecord.INSTANCE_ID))
                .operationType(AsyncProfilerTaskLogOperationType.parse(data.getInt(AsyncProfilerTaskLogRecord.OPERATION_TYPE)))
                .operationTime(data.getLong(AsyncProfilerTaskLogRecord.OPERATION_TIME))
                .build();
    }
}
