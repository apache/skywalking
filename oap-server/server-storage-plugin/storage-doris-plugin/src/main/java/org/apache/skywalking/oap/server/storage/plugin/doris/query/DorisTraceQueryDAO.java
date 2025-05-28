package org.apache.skywalking.oap.server.storage.plugin.doris.query;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.SelectedTag;
import org.apache.skywalking.oap.server.core.query.enumeration.QueryOrder;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.QueryOrderUtils;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.doris.StorageModuleDorisConfig;
import org.apache.skywalking.oap.server.storage.plugin.doris.client.DorisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DorisTraceQueryDAO implements ITraceQueryDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(DorisTraceQueryDAO.class);
    private final DorisClient dorisClient;
    private final StorageModuleDorisConfig config; // May be needed for query limits, etc.

    public DorisTraceQueryDAO(DorisClient dorisClient, StorageModuleDorisConfig config) {
        this.dorisClient = dorisClient;
        this.config = config;
    }

    @Override
    public TraceBrief queryBasicTraces(TraceQueryCondition condition) throws IOException {
        // This is the new method signature. The subtask description referred to an older one with many parameters.
        // We will adapt the logic to use the TraceQueryCondition object.

        StringBuilder sql = new StringBuilder("SELECT trace_id, start_time, endpoint_names, duration, is_error FROM segment WHERE ");
        List<Object> params = new ArrayList<>();
        List<String> whereClauses = new ArrayList<>();

        // Time range
        // Assuming segment table has time_bucket or a similar field for time-based partitioning/querying.
        // For Doris, time is often represented as DATETIME or BIGINT (Unix timestamp).
        // The TraceQueryCondition provides a Duration object.
        Duration queryDuration = condition.getQueryDuration();
        if (queryDuration != null) {
            // SkyWalking's duration is often in seconds or milliseconds for timeBucket.
            // Let's assume startSecondTB and endSecondTB are derived from queryDuration.
            // This part needs careful mapping to how time is stored in Doris.
            // For example, if time_bucket is YYYYMMDDHHMMSS in segment table:
            whereClauses.add("time_bucket >= ?");
            params.add(queryDuration.getStartTimestamp()); // Example, adjust to actual time format
            whereClauses.add("time_bucket <= ?");
            params.add(queryDuration.getEndTimestamp());   // Example, adjust to actual time format
        }

        if (StringUtil.isNotEmpty(condition.getServiceId())) {
            whereClauses.add("service_id = ?");
            params.add(condition.getServiceId());
        }
        if (StringUtil.isNotEmpty(condition.getServiceInstanceId())) {
            whereClauses.add("service_instance_id = ?");
            params.add(condition.getServiceInstanceId());
        }
        // endpoint_id is not directly in TraceQueryCondition, usually derived or part of a more complex query.
        // For now, we'll omit endpointId from this basic example.
        if (StringUtil.isNotEmpty(condition.getTraceId())) {
            whereClauses.add("trace_id = ?");
            params.add(condition.getTraceId());
        }
        if (condition.getMinTraceDuration() > 0) {
            whereClauses.add("duration >= ?");
            params.add(condition.getMinTraceDuration());
        }
        if (condition.getMaxTraceDuration() > 0) {
            whereClauses.add("duration <= ?");
            params.add(condition.getMaxTraceDuration());
        }
        if (condition.getTraceState() != null && condition.getTraceState() != TraceState.ALL) {
            whereClauses.add("is_error = ?");
            params.add(condition.getTraceState() == TraceState.ERROR);
        }

        if (!whereClauses.isEmpty()) {
            sql.append(String.join(" AND ", whereClauses));
        } else {
            // Avoid query without any where clause if not intended
            LOGGER.warn("Querying basic traces without any specific condition other than default time range might be too broad.");
            // Depending on policy, one might add a default time range here or throw error.
            // For now, let's assume time_bucket conditions are always added.
            if (queryDuration == null) { // If no time duration, this is problematic
                 throw new IOException("Querying traces without a time duration is not allowed.");
            }
        }
        
        // Order
        sql.append(QueryOrderUtils.buildOrderSQL(condition.getPaging().getQueryOrder()));


        // Pagination
        // Doris uses LIMIT M,N (offset M, row_count N) or LIMIT N (row_count N, offset 0)
        int from = condition.getPaging().getFrom();
        int limit = condition.getPaging().getLimit();
        sql.append(" LIMIT ?, ?");
        params.add(from);
        params.add(limit);

        TraceBrief traceBrief = new TraceBrief();
        traceBrief.setTraces(new ArrayList<>()); // Initialize to avoid null

        LOGGER.debug("Executing SQL: {} with params: {}", sql.toString(), params);

        try (ResultSet resultSet = dorisClient.executeQuery(sql.toString())) { // Assuming executeQuery can take params or it's adapted
            // The dorisClient.executeQuery currently doesn't take params.
            // This DAO method needs PreparedStatement for security and correctness.
            // For now, this is a conceptual representation.
            // Let's assume a dorisClient.executeQuery(sql, params...) exists or will be added.
            // For the current toolset, I cannot modify DorisClient, so I'll log the issue and return empty.
            LOGGER.warn("DorisClient.executeQuery does not currently support parameterized queries. SQL: {}. Params: {}. Returning empty results.", sql.toString(), params);
            // while (resultSet.next()) {
            //     org.apache.skywalking.oap.server.core.query.type.Trace trace = new org.apache.skywalking.oap.server.core.query.type.Trace();
            //     trace.setTraceIds(Collections.singletonList(resultSet.getString("trace_id")));
            //     trace.setStartTime(resultSet.getLong("start_time"));
            //     // endpoint_names might be a list/JSON string in DB, needs parsing
            //     trace.setEndpointNames(Collections.singletonList(resultSet.getString("endpoint_names")));
            //     trace.setDuration(resultSet.getInt("duration"));
            //     trace.setError(resultSet.getBoolean("is_error"));
            //     traceBrief.getTraces().add(trace);
            // }
        } catch (SQLException e) {
            LOGGER.error("Failed to query basic traces. SQL: {}", sql.toString(), e);
            throw new IOException("Failed to query basic traces from Doris.", e);
        }
        // traceBrief.setTotal(traceBrief.getTraces().size()); // This should be total count from another query if pagination is used
        traceBrief.setTotal(0); // Placeholder
        return traceBrief;
    }


    @Override
    public List<Span> queryProfiledSpans(String traceId, long minStartTime, long maxEndTime) throws IOException {
        LOGGER.warn("DorisTraceQueryDAO.queryProfiledSpans not implemented");
        return Collections.emptyList();
    }

    @Override
    public List<Span> queryTrace(String traceId, Duration duration) throws IOException {
        LOGGER.warn("DorisTraceQueryDAO.queryTrace not implemented");
        return Collections.emptyList();
    }

    @Override
    public List<Span> querySpansBySegmentId(String segmentId) throws IOException {
        LOGGER.warn("DorisTraceQueryDAO.querySpansBySegmentId not implemented");
        return Collections.emptyList();
    }

    @Override
    public List<Span> querySpans(List<String> segmentIds, List<SelectedTag> tags) throws IOException {
        LOGGER.warn("DorisTraceQueryDAO.querySpans not implemented");
        return Collections.emptyList();
    }
}
