package org.apache.skywalking.oap.server.storage.plugin.doris.query;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
// Import SkyWalking's actual SpanObject and SegmentObject if available and preferred.
// For now, using placeholder data classes for deserialization structure.
// import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
// import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.core.query.type.LogEntity;
import org.apache.skywalking.oap.server.core.query.type.Ref;
import org.apache.skywalking.oap.server.core.query.type.RefType;
import org.apache.skywalking.oap.server.core.query.enumeration.QueryOrder;
import org.apache.skywalking.oap.server.core.query.input.Duration;
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
    private final StorageModuleDorisConfig config;
    private final Gson gson = new Gson();

    private static final String SEGMENT_TABLE = "segment";
    private static final String TRACE_ID = "trace_id";
    private static final String SEGMENT_ID = "segment_id";
    private static final String SERVICE_ID = "service_id";
    private static final String SERVICE_INSTANCE_ID = "service_instance_id";
    private static final String ENDPOINT_ID = "endpoint_id";
    private static final String START_TIME = "start_time";
    private static final String END_TIME = "end_time";
    private static final String DURATION = "duration";
    private static final String IS_ERROR = "is_error";
    private static final String DATA_BINARY = "data_binary";
    private static final String TIME_BUCKET = "time_bucket";
    // private static final String TAGS = "tags"; // Not used yet

    // Placeholder data classes for deserializing data_binary JSON
    // These should ideally match the structure of SkyWalking's SegmentObject and SpanObject
    // if we are deserializing from a JSON representation of those.
    private static class SegmentObjectData {
        private String traceId;
        private String segmentId;
        private List<SpanObjectData> spans;
        // Add other fields from SegmentObject if needed for context
    }

    private static class SpanObjectData {
        private int spanId;
        private int parentSpanId;
        private long startTime;
        private long endTime;
        private String operationName;
        private String peer;
        private String spanLayer; // e.g., "Http", "RPCFramework"
        private boolean isError;
        private String componentName; // from componentId or component annotation
        private List<LogEventData> logs;
        private List<TagData> tags;
        private List<RefData> refs;
        // Add other fields like componentId, spanType, etc.
    }
    
    private static class TagData {
        private String key;
        private String value;
    }

    private static class LogEventData {
        private long time;
        private List<TagData> data;
    }
    
    private static class RefData {
        private String traceId;
        private String parentSegmentId;
        private int parentSpanId;
        private String type; // "CrossProcess" or "CrossThread"
    }


    public DorisTraceQueryDAO(DorisClient dorisClient, StorageModuleDorisConfig config) {
        this.dorisClient = dorisClient;
        this.config = config;
    }

    @Override
    public TraceBrief queryBasicTraces(Duration duration, long minDuration, long maxDuration,
                                       String serviceId, String serviceInstanceId, String endpointName, // Changed endpointId to endpointName for clarity
                                       String traceId, int limit, int from,
                                       TraceState traceState, QueryOrder queryOrder, List<Tag> tags) throws IOException {
        
        StringBuilder sqlBuilder = new StringBuilder("SELECT ");
        sqlBuilder.append(TRACE_ID).append(", ")
                  .append(START_TIME).append(", ")
                  // Assuming an endpoint_name column exists or endpoint_id is used as placeholder
                  .append(ENDPOINT_ID).append(" as endpoint_name, ") 
                  .append(DURATION).append(", ")
                  .append(IS_ERROR);
        sqlBuilder.append(" FROM ").append(SEGMENT_TABLE).append(" WHERE 1=1 "); // Start with 1=1 to simplify appending AND

        List<Object> params = new ArrayList<>();

        if (duration != null) {
            sqlBuilder.append(" AND ").append(START_TIME).append(" >= ?");
            params.add(duration.getStartTimestamp());
            sqlBuilder.append(" AND ").append(START_TIME).append(" <= ?");
            params.add(duration.getEndTimestamp());
        }
        
        if (minDuration > 0) {
            sqlBuilder.append(" AND ").append(DURATION).append(" >= ?");
            params.add(minDuration);
        }
        if (maxDuration > 0) {
            sqlBuilder.append(" AND ").append(DURATION).append(" <= ?");
            params.add(maxDuration);
        }
        if (StringUtil.isNotEmpty(serviceId)) {
            sqlBuilder.append(" AND ").append(SERVICE_ID).append(" = ?");
            params.add(serviceId);
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            sqlBuilder.append(" AND ").append(SERVICE_INSTANCE_ID).append(" = ?");
            params.add(serviceInstanceId);
        }
        if (StringUtil.isNotEmpty(endpointName)) {
            // Assuming a query on an endpoint_name column or that ENDPOINT_ID stores the name.
            sqlBuilder.append(" AND ").append(ENDPOINT_ID).append(" = ?"); 
            params.add(endpointName);
        }
        if (StringUtil.isNotEmpty(traceId)) {
            sqlBuilder.append(" AND ").append(TRACE_ID).append(" = ?");
            params.add(traceId);
        }
        if (traceState != null && traceState != TraceState.ALL) {
            sqlBuilder.append(" AND ").append(IS_ERROR).append(" = ?");
            params.add(traceState == TraceState.ERROR);
        }

        if (tags != null && !tags.isEmpty()) {
            for (Tag tag : tags) {
                // This requires a schema where tags are queryable. Example: tags stored as JSON "tags_map"
                // sqlBuilder.append(" AND JSON_EXTRACT_STRING(tags_map, ?) = ?");
                // params.add(tag.getKey());
                // params.add(tag.getValue());
                // Or if specific tag columns exist: sqlBuilder.append(" AND tag_").append(tag.getKey()).append(" = ?");
                LOGGER.warn("Tag-based filtering in queryBasicTraces is not implemented for Doris. Tag: {}:{}", tag.getKey(), tag.getValue());
            }
        }

        // Count total matching records for pagination (before applying limit/offset)
        long total = 0;
        String countSql = sqlBuilder.toString().replaceFirst("SELECT .*? FROM", "SELECT COUNT(DISTINCT " + TRACE_ID + ") FROM");
        // Remove order by for count query if it was added before
        if (countSql.contains("ORDER BY")) {
            countSql = countSql.substring(0, countSql.indexOf("ORDER BY"));
        }

        try (ResultSet countResultSet = dorisClient.executeQuery(countSql, params.toArray())) {
            if (countResultSet != null && countResultSet.next()) {
                total = countResultSet.getLong(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to count basic traces. SQL: {}. Error: {}", countSql, e.getMessage(), e);
            // Continue without total, or throw error. For now, total will be 0.
        }


        String orderByColumn;
        switch (queryOrder) {
            case BY_DURATION:
                orderByColumn = DURATION;
                break;
            case BY_START_TIME:
            default:
                orderByColumn = START_TIME;
                break;
        }
        sqlBuilder.append(" ORDER BY ").append(orderByColumn).append(" DESC");

        sqlBuilder.append(" LIMIT ?, ?");
        params.add(from); 
        params.add(limit);

        TraceBrief traceBrief = new TraceBrief();
        traceBrief.setTotal((int)total); // Set the actual total
        traceBrief.setTraces(new ArrayList<>());

        String querySql = sqlBuilder.toString();
        LOGGER.debug("Executing queryBasicTraces SQL: {} with params: {}", querySql, params);

        try (ResultSet resultSet = dorisClient.executeQuery(querySql, params.toArray())) {
            while (resultSet != null && resultSet.next()) {
                org.apache.skywalking.oap.server.core.query.type.Trace trace = new org.apache.skywalking.oap.server.core.query.type.Trace();
                trace.setTraceIds(Collections.singletonList(resultSet.getString(TRACE_ID)));
                trace.setStartTime(resultSet.getLong(START_TIME));
                trace.getEndpointNames().add(resultSet.getString("endpoint_name"));
                trace.setDuration(resultSet.getInt(DURATION));
                trace.setError(resultSet.getBoolean(IS_ERROR));
                traceBrief.getTraces().add(trace);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to query basic traces. SQL: {}. Error: {}", querySql, e.getMessage(), e);
            throw new IOException("Failed to query basic traces from Doris.", e);
        }
        return traceBrief;
    }
    
    private SegmentRecord mapResultSetToSegmentRecord(ResultSet rs) throws SQLException {
        SegmentRecord record = new SegmentRecord();
        record.setSegmentId(rs.getString(SEGMENT_ID));
        record.setTraceId(rs.getString(TRACE_ID));
        record.setServiceId(rs.getString(SERVICE_ID));
        record.setServiceInstanceId(rs.getString(SERVICE_INSTANCE_ID));
        record.setStartTime(rs.getLong(START_TIME));
        record.setEndTime(rs.getLong(END_TIME));
        record.setIsError(rs.getInt(IS_ERROR)); 
        record.setTimeBucket(rs.getLong(TIME_BUCKET)); 

        String dataBinaryString = rs.getString(DATA_BINARY);
        if (StringUtil.isNotEmpty(dataBinaryString)) {
            // Assuming DATA_BINARY column in Doris is TEXT/STRING and stores plain JSON string.
            record.setDataBinary(dataBinaryString.getBytes());
        } else {
            record.setDataBinary(new byte[]{}); // Ensure dataBinary is not null
        }
        return record;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId, @Nullable Duration duration) throws IOException {
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM ").append(SEGMENT_TABLE);
        List<Object> params = new ArrayList<>();
        sqlBuilder.append(" WHERE ").append(TRACE_ID).append(" = ?");
        params.add(traceId);

        if (duration != null) {
            sqlBuilder.append(" AND ").append(START_TIME).append(" >= ? AND ").append(START_TIME).append(" <= ?");
            params.add(duration.getStartTimestamp());
            params.add(duration.getEndTimestamp());
        }
        sqlBuilder.append(" ORDER BY ").append(START_TIME).append(" ASC");
        // Consider a reasonable limit for safety, e.g., config.getTraceQueryMaxSegmentsPerTrace()
        // sqlBuilder.append(" LIMIT 100"); // Example fixed limit

        List<SegmentRecord> segmentRecords = new ArrayList<>();
        String querySql = sqlBuilder.toString();
        LOGGER.debug("Executing queryByTraceId SQL: {} with params: {}", querySql, params);

        try (ResultSet resultSet = dorisClient.executeQuery(querySql, params.toArray())) {
            while (resultSet != null && resultSet.next()) {
                segmentRecords.add(mapResultSetToSegmentRecord(resultSet));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to query segments by traceId {}. SQL: {}. Error: {}", traceId, querySql, e.getMessage(), e);
            throw new IOException("Failed to query segments by traceId from Doris: " + traceId, e);
        }
        return segmentRecords;
    }

    @Override
    public List<SegmentRecord> queryBySegmentIdList(List<String> segmentIdList, @Nullable Duration duration) throws IOException {
        if (segmentIdList == null || segmentIdList.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM ").append(SEGMENT_TABLE);
        List<Object> params = new ArrayList<>();
        
        sqlBuilder.append(" WHERE ").append(SEGMENT_ID).append(" IN (");
        sqlBuilder.append(segmentIdList.stream().map(id -> "?").collect(Collectors.joining(", ")));
        sqlBuilder.append(")");
        params.addAll(segmentIdList);

        if (duration != null) {
            sqlBuilder.append(" AND ").append(START_TIME).append(" >= ? AND ").append(START_TIME).append(" <= ?");
            params.add(duration.getStartTimestamp());
            params.add(duration.getEndTimestamp());
        }
        sqlBuilder.append(" ORDER BY ").append(START_TIME).append(" ASC");

        List<SegmentRecord> segmentRecords = new ArrayList<>();
        String querySql = sqlBuilder.toString();
        LOGGER.debug("Executing queryBySegmentIdList SQL: {} with params: {}", querySql, params);
        
        try (ResultSet resultSet = dorisClient.executeQuery(querySql, params.toArray())) {
            while (resultSet != null && resultSet.next()) {
                segmentRecords.add(mapResultSetToSegmentRecord(resultSet));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to query segments by segmentIdList. SQL: {}. Error: {}", querySql, e.getMessage(), e);
            throw new IOException("Failed to query segments by segmentIdList from Doris.", e);
        }
        return segmentRecords;
    }
    
    @Override
    public List<SegmentRecord> queryByTraceIdWithInstanceId(List<String> traceIdList, List<String> instanceIdList, @Nullable Duration duration) throws IOException {
        if (traceIdList == null || traceIdList.isEmpty() || instanceIdList == null || instanceIdList.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM ").append(SEGMENT_TABLE);
        List<Object> params = new ArrayList<>();
        sqlBuilder.append(" WHERE ").append(TRACE_ID).append(" IN (");
        sqlBuilder.append(traceIdList.stream().map(id -> "?").collect(Collectors.joining(", ")));
        sqlBuilder.append(")");
        params.addAll(traceIdList);

        sqlBuilder.append(" AND ").append(SERVICE_INSTANCE_ID).append(" IN (");
        sqlBuilder.append(instanceIdList.stream().map(id -> "?").collect(Collectors.joining(", ")));
        sqlBuilder.append(")");
        params.addAll(instanceIdList);
        
        if (duration != null) {
            sqlBuilder.append(" AND ").append(START_TIME).append(" >= ? AND ").append(START_TIME).append(" <= ?");
            params.add(duration.getStartTimestamp());
            params.add(duration.getEndTimestamp());
        }
        sqlBuilder.append(" ORDER BY ").append(START_TIME).append(" ASC");

        List<SegmentRecord> segmentRecords = new ArrayList<>();
        String querySql = sqlBuilder.toString();
        LOGGER.debug("Executing queryByTraceIdWithInstanceId SQL: {} with params: {}", querySql, params);

        try (ResultSet resultSet = dorisClient.executeQuery(querySql, params.toArray())) {
            while (resultSet != null && resultSet.next()) {
                segmentRecords.add(mapResultSetToSegmentRecord(resultSet));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to query segments by traceIdList and instanceIdList. SQL: {}. Error: {}", querySql, e.getMessage(), e);
            throw new IOException("Failed to query segments by traceIdList and instanceIdList from Doris.", e);
        }
        return segmentRecords;
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        List<SegmentRecord> segmentRecords = queryByTraceId(traceId, null);
        List<Span> allSpans = new ArrayList<>();

        for (SegmentRecord segmentRecord : segmentRecords) {
            if (segmentRecord.getDataBinary() == null || segmentRecord.getDataBinary().length == 0) {
                continue;
            }
            try {
                // Assuming data_binary stores JSON string of SegmentObjectData
                String segmentJson = new String(segmentRecord.getDataBinary());
                SegmentObjectData segmentObjectData = gson.fromJson(segmentJson, SegmentObjectData.class);

                if (segmentObjectData != null && segmentObjectData.spans != null) {
                    for (SpanObjectData spanData : segmentObjectData.spans) {
                        Span uiSpan = new Span();
                        uiSpan.setTraceId(segmentRecord.getTraceId()); // Or segmentObjectData.traceId
                        uiSpan.setSegmentId(segmentRecord.getSegmentId()); // Or segmentObjectData.segmentId
                        uiSpan.setSpanId(spanData.spanId);
                        uiSpan.setParentSpanId(spanData.parentSpanId);
                        uiSpan.setStartTime(spanData.startTime);
                        uiSpan.setEndTime(spanData.endTime);
                        uiSpan.setOperationName(spanData.operationName);
                        uiSpan.setPeer(spanData.peer);
                        uiSpan.setSpanLayer(spanData.spanLayer);
                        uiSpan.setError(spanData.isError);
                        uiSpan.setComponent(spanData.componentName);
                        // Convert tags
                        if (spanData.tags != null) {
                            spanData.tags.forEach(tag -> uiSpan.getTags().add(new KeyValue(tag.key, tag.value)));
                        }
                        // Convert logs
                        if (spanData.logs != null) {
                            spanData.logs.forEach(log -> {
                                LogEntity logEntity = new LogEntity();
                                logEntity.setTime(log.time);
                                if (log.data != null) {
                                    log.data.forEach(logTag -> logEntity.getData().add(new KeyValue(logTag.key, logTag.value)));
                                }
                                uiSpan.getLogs().add(logEntity);
                            });
                        }
                        // Convert refs
                        if (spanData.refs != null) {
                            spanData.refs.forEach(refData -> {
                                Ref uiRef = new Ref();
                                uiRef.setTraceId(refData.traceId);
                                uiRef.setParentSegmentId(refData.parentSegmentId);
                                uiRef.setParentSpanId(refData.parentSpanId);
                                try {
                                    uiRef.setType(RefType.valueOf(refData.type));
                                } catch (IllegalArgumentException e) {
                                    LOGGER.warn("Unknown RefType: {} for traceId: {}", refData.type, refData.traceId);
                                     uiRef.setType(RefType.CROSS_PROCESS); // Default or handle error
                                }
                                uiSpan.getRefs().add(uiRef);
                            });
                        }
                        // Need to map serviceId to serviceCode for uiSpan.setServiceCode()
                        // This requires access to IServiceQueryDAO or similar service for ID to Name/Code mapping.
                        // For now, leaving serviceCode unset or using serviceId directly if that's acceptable.
                        uiSpan.setServiceCode(segmentRecord.getServiceId()); // Placeholder: using service_id as service_code

                        allSpans.add(uiSpan);
                    }
                }
            } catch (JsonSyntaxException e) {
                LOGGER.error("Failed to parse data_binary JSON for segment {} in trace {}: {}", segmentRecord.getSegmentId(), traceId, e.getMessage(), e);
            } catch (Exception e) {
                LOGGER.error("Unexpected error during span conversion for segment {} in trace {}: {}", segmentRecord.getSegmentId(), traceId, e.getMessage(), e);
            }
        }
        // Sort spans by start time, then spanId, as is common for UI display
        allSpans.sort((s1, s2) -> {
            if (s1.getStartTime() != s2.getStartTime()) {
                return Long.compare(s1.getStartTime(), s2.getStartTime());
            }
            return Integer.compare(s1.getSpanId(), s2.getSpanId());
        });
        return allSpans;
    }
}
