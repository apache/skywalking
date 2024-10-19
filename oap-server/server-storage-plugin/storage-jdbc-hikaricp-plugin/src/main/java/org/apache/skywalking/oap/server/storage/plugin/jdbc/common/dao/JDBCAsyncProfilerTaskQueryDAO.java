package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerEventType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class JDBCAsyncProfilerTaskQueryDAO implements IAsyncProfilerTaskQueryDAO {

    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<AsyncProfilerTask> getTaskList(String serviceId, Long startTimeBucket, Long endTimeBucket, Integer limit) throws IOException {
        if (StringUtil.isBlank(serviceId)) {
            return new ArrayList<>();
        }
        final var results = new ArrayList<AsyncProfilerTask>();
        final var tables = startTimeBucket == null || endTimeBucket == null ?
                tableHelper.getTablesWithinTTL(AsyncProfilerTaskRecord.INDEX_NAME) :
                tableHelper.getTablesForRead(AsyncProfilerTaskRecord.INDEX_NAME, startTimeBucket, endTimeBucket);
        for (final var table : tables) {
            List<Object> condition = new ArrayList<>(4);
            StringBuilder sql = new StringBuilder()
                    .append("select * from ").append(table)
                    .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            condition.add(AsyncProfilerTaskRecord.INDEX_NAME);

            sql.append(" and ").append(AsyncProfilerTaskRecord.SERVICE_ID).append("=? ");
            condition.add(serviceId);

            if (startTimeBucket != null) {
                sql.append(" and ").append(AsyncProfilerTaskRecord.TIME_BUCKET).append(" >= ? ");
                condition.add(startTimeBucket);
            }

            if (endTimeBucket != null) {
                sql.append(" and ").append(AsyncProfilerTaskRecord.TIME_BUCKET).append(" <= ? ");
                condition.add(endTimeBucket);
            }

            sql.append(" ORDER BY ").append(AsyncProfilerTaskRecord.CREATE_TIME).append(" DESC ");

            if (limit != null) {
                sql.append(" LIMIT ").append(limit);
            }

            results.addAll(
                    jdbcClient.executeQuery(
                            sql.toString(),
                            resultSet -> {
                                final var tasks = new ArrayList<AsyncProfilerTask>();
                                while (resultSet.next()) {
                                    tasks.add(buildAsyncProfilerTask(resultSet));
                                }
                                return tasks;
                            },
                            condition.toArray(new Object[0]))
            );
        }
        return limit == null ?
                results :
                results
                        .stream()
                        .limit(limit)
                        .collect(toList());
    }

    @Override
    @SneakyThrows
    public AsyncProfilerTask getById(String id) throws IOException {
        final var tables = tableHelper.getTablesWithinTTL(AsyncProfilerTaskRecord.INDEX_NAME);
        for (String table : tables) {
            final StringBuilder sql = new StringBuilder();
            final List<Object> condition = new ArrayList<>(1);
            sql.append("select * from ").append(table)
                    .append(" where ")
                    .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ")
                    .append(" and ")
                    .append(AsyncProfilerTaskRecord.TASK_ID + "=? LIMIT 1");
            condition.add(AsyncProfilerTaskRecord.INDEX_NAME);
            condition.add(id);

            final var r = jdbcClient.executeQuery(
                    sql.toString(),
                    resultSet -> {
                        if (resultSet.next()) {
                            return buildAsyncProfilerTask(resultSet);
                        }
                        return null;
                    },
                    condition.toArray(new Object[0]));
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    private AsyncProfilerTask buildAsyncProfilerTask(ResultSet data) throws SQLException {
        List<String> events = (List<String>) data.getObject(AsyncProfilerTaskRecord.EVENT_TYPES);

        return AsyncProfilerTask.builder()
                .id(data.getString(AsyncProfilerTaskRecord.TASK_ID))
                .serviceId(data.getString(AsyncProfilerTaskRecord.SERVICE_ID))
                .serviceInstanceIds((List<String>) data.getObject(AsyncProfilerTaskRecord.SERVICE_INSTANCE_IDS))
                .createTime(data.getLong(AsyncProfilerTaskRecord.CREATE_TIME))
                .duration(data.getInt(AsyncProfilerTaskRecord.DURATION))
                .events(AsyncProfilerEventType.valueOfList(events))
                .execArgs(data.getString(AsyncProfilerTaskRecord.EXEC_ARGS))
                .build();
    }
}
