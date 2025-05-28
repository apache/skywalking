package org.apache.skywalking.oap.server.storage.plugin.doris;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;
import org.apache.skywalking.oap.server.core.query.enumeration.QueryOrder;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Paging;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.doris.dao.DorisBatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.doris.dao.DorisRecordDAO;
import org.apache.skywalking.oap.server.storage.plugin.doris.query.DorisTraceQueryDAO;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class DorisTraceQueryAndWriteIT extends DorisStoragePluginITBase {

    private DorisRecordDAO recordDAO;
    private DorisTraceQueryDAO traceQueryDAO;
    private DorisBatchDAO batchDAO;
    private Gson gson = new Gson();

    @Mock
    private ModuleManager moduleManager; // For TraceQueryCondition setup
    @Mock
    private ConfigService configService; // For TraceQueryCondition setup

    @BeforeEach
    public void setUpDAOs() {
        recordDAO = new DorisRecordDAO(getDorisClient());
        traceQueryDAO = new DorisTraceQueryDAO(getDorisClient(), getDorisConfig());
        batchDAO = new DorisBatchDAO(getDorisClient(), getDorisConfig());

        // Mock services needed by TraceQueryCondition
        when(moduleManager.find(CoreModule.NAME)).thenReturn(null); // Avoids NPE if it tries to get CoreModule
        // If TraceQueryCondition directly uses ConfigService:
        // when(configService.getSearchableTracesTags()).thenReturn(""); // Example
    }

    private Model createSegmentModel() {
        List<ModelColumn> columns = List.of(
            new ModelColumn("trace_id", String.class, false, true, false, 255),
            new ModelColumn("segment_id", String.class, true, true, true, 255),
            new ModelColumn("service_id", String.class, false, true, false, 255),
            new ModelColumn("service_instance_id", String.class, false, true, false, 255),
            new ModelColumn("endpoint_id", String.class, false, true, false, 255),
            new ModelColumn("start_time", Long.class, false, true, false, 0),
            new ModelColumn("end_time", Long.class, false, true, false, 0),
            new ModelColumn("duration", Integer.class, false, true, false, 0),
            new ModelColumn("is_error", Integer.class, false, true, false, 0), // TINYINT maps to Integer for 0/1
            new ModelColumn("data_binary", String.class, false, true, false, 0), // TEXT maps to String
            new ModelColumn("version", Integer.class, false, true, false, 0),
            new ModelColumn("tags", String.class, false, true, false, 0), // TEXT maps to String
            new ModelColumn("time_bucket", Long.class, false, true, true, 0)
        );
        return new Model(
            "segment", // Table name from doris_schema.sql
            columns,
            Collections.emptyList(),
            false, // Not a super dataset model
            Downsampling.Minute, // Default, actual downsampling depends on time_bucket source
            true, // Is a record (not metrics)
            SegmentRecord.class.getName(), // Source class
            new SQLDatabaseModelExtension()
        );
    }

    // Placeholder for span data structure if we deserialize from data_binary
    private static class TestSpanData {
        String operationName;
        long startTime;
        // Add other fields if needed for assertion
    }

    @Test
    public void testWriteAndReadTraceSegment() throws IOException, InterruptedException {
        Model segmentModel = createSegmentModel();
        long testStartTime = System.currentTimeMillis() - 10000;
        long testEndTime = testStartTime + 5000;
        int testDuration = 5000;
        String testTraceId = "test-trace-id-" + System.nanoTime(); // Ensure unique trace ID per run
        String testSegmentId = "test-segment-id-" + System.nanoTime();
        String testServiceId = IDManager.ServiceID.buildId("service-A", true); // Generate ID as SkyWalking would
        String testInstanceId = IDManager.ServiceInstanceID.buildId(testServiceId, "instance-X");

        // Time bucket in YYYYMMDDHHMM format from start_time
        long timeBucket = Long.parseLong(TimeBucket.getTimeBucket(testStartTime, Downsampling.Minute));

        List<TestSpanData> spansForBinary = new ArrayList<>();
        TestSpanData span1 = new TestSpanData();
        span1.operationName = "/test/endpoint1";
        span1.startTime = testStartTime + 100;
        spansForBinary.add(span1);
        String dataBinaryJson = gson.toJson(spansForBinary);

        Record segmentRecordMap = new Record();
        segmentRecordMap.put("trace_id", testTraceId);
        segmentRecordMap.put("segment_id", testSegmentId);
        segmentRecordMap.put("service_id", testServiceId);
        segmentRecordMap.put("service_instance_id", testInstanceId);
        segmentRecordMap.put("endpoint_id", IDManager.EndpointID.buildId(testServiceId, "/test/endpoint1")); // Example
        segmentRecordMap.put("start_time", testStartTime);
        segmentRecordMap.put("end_time", testEndTime);
        segmentRecordMap.put("duration", testDuration);
        segmentRecordMap.put("is_error", 0); // 0 for false
        segmentRecordMap.put("data_binary", dataBinaryJson);
        segmentRecordMap.put("version", 3); // SW v8/v9 segment version
        segmentRecordMap.put("tags", null); // Example: "[{\"key\":\"http.method\",\"value\":\"GET\"}]"
        segmentRecordMap.put("time_bucket", timeBucket);

        // 1. Write Segment
        InsertRequest insertRequest = recordDAO.prepareBatchInsert(segmentModel, segmentRecordMap);
        batchDAO.insert(insertRequest); // Or use flush for a list

        Thread.sleep(2000); // Increased wait time for Doris visibility

        // 2. Query Segment by Trace ID
        List<SegmentRecord> retrievedSegments = traceQueryDAO.queryByTraceId(testTraceId, null);
        Assertions.assertNotNull(retrievedSegments);
        Assertions.assertFalse(retrievedSegments.isEmpty(), "No segments found for traceId: " + testTraceId);
        Assertions.assertEquals(1, retrievedSegments.size(), "Should find exactly one segment for traceId: " + testTraceId);

        SegmentRecord retrievedSegment = retrievedSegments.get(0);
        Assertions.assertEquals(testTraceId, retrievedSegment.getTraceId());
        Assertions.assertEquals(testSegmentId, retrievedSegment.getSegmentId());
        Assertions.assertEquals(testServiceId, retrievedSegment.getServiceId());
        Assertions.assertEquals(testInstanceId, retrievedSegment.getServiceInstanceId());
        Assertions.assertEquals(testDuration, retrievedSegment.getDuration());
        Assertions.assertEquals(0, retrievedSegment.getIsError());
        Assertions.assertEquals(timeBucket, retrievedSegment.getTimeBucket());

        // Optional: Deserialize data_binary and check span
        if (retrievedSegment.getDataBinary() != null && retrievedSegment.getDataBinary().length > 0) {
            String retrievedDataBinaryJson = new String(retrievedSegment.getDataBinary());
            TestSpanData[] deserializedSpans = gson.fromJson(retrievedDataBinaryJson, TestSpanData[].class);
            Assertions.assertNotNull(deserializedSpans);
            Assertions.assertTrue(deserializedSpans.length > 0, "No spans found in deserialized data_binary");
            Assertions.assertEquals("/test/endpoint1", deserializedSpans[0].operationName);
        } else {
            Assertions.fail("data_binary was null or empty in the retrieved segment.");
        }

        // 3. Query using Basic Traces
        // Duration for queryBasicTraces should encompass the segment's start_time
        Duration queryDuration = new Duration();
        queryDuration.setStartTimestamp(TimeBucket.getTimeBucket(testStartTime - TimeUnit.SECONDS.toMillis(10), Downsampling.Second));
        queryDuration.setEndTimestamp(TimeBucket.getTimeBucket(testEndTime + TimeUnit.SECONDS.toMillis(10), Downsampling.Second));
        
        Paging paging = new Paging();
        paging.setPageNum(1);
        paging.setPageSize(10);

        TraceQueryCondition condition = new TraceQueryCondition();
        condition.setTraceId(testTraceId);
        condition.setQueryDuration(queryDuration);
        condition.setPaging(paging);
        condition.setTraceState(TraceState.ALL);
        condition.setQueryOrder(QueryOrder.BY_START_TIME);
        // condition.setServiceId(testServiceId); // Can also filter by service_id

        // The queryBasicTraces in ITraceQueryDAO takes many parameters, not TraceQueryCondition.
        // My DorisTraceQueryDAO has the one with many params.
        TraceBrief brief = traceQueryDAO.queryBasicTraces(
            queryDuration,
            0, // minDuration
            0, // maxDuration (0 means no filter on max duration)
            testServiceId,
            null, // serviceInstanceId - optional
            null, // endpointName - optional
            testTraceId,
            10,   // limit
            0,    // from (offset)
            TraceState.ALL,
            QueryOrder.BY_START_TIME,
            null  // tags - optional
        );

        Assertions.assertNotNull(brief, "TraceBrief should not be null");
        Assertions.assertNotNull(brief.getTraces(), "TraceBrief.traces should not be null");
        Assertions.assertFalse(brief.getTraces().isEmpty(), "No basic traces found for traceId: " + testTraceId);
        boolean traceFoundInBrief = brief.getTraces().stream()
            .anyMatch(trace -> trace.getTraceIds().contains(testTraceId));
        Assertions.assertTrue(traceFoundInBrief, "Written traceId " + testTraceId + " not found in queryBasicTraces result.");
    }
}
