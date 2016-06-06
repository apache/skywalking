package test.com.ai.skywalking.api;

import com.ai.cloud.skywalking.protocol.Span;

import java.util.List;

public class TraceTreeAssert {

    public static void assertEquals(String[][] traceTree) {
        List<Span> traceSpanList = TraceTreeDataAcquirer.acquireCurrentTraceSpanData();

        validateTraceId(traceSpanList);
        validateSpanData(traceSpanList, traceTree);
    }

    private static void validateSpanData(List<Span> traceSpanList, String[][] traceTree) {

    }

    private static void validateTraceId(List<Span> traceSpanList) {
    }
}
