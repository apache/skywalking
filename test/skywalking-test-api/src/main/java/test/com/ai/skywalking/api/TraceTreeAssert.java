package test.com.ai.skywalking.api;

import com.ai.cloud.skywalking.protocol.Span;

import java.util.List;

public class TraceTreeAssert {

    public static void assertEquals(String[][] assertTraceTree) {
        List<Span> bufferTraceSpanList = TraceTreeDataAcquirer.acquireCurrentTraceSpanData();
        validateTraceId(bufferTraceSpanList);
        validateTraceSpanSize(bufferTraceSpanList.size(), assertTraceTree.length);
        validateSpanData(bufferTraceSpanList, assertTraceTree);
    }

    private static void validateTraceSpanSize(int actualSpanSize, int expectedSpanSize) {
        if (actualSpanSize != expectedSpanSize) {
            throw new RuntimeException("expected span size : " + expectedSpanSize + "\n actual span size : " + actualSpanSize);
        }
    }

    private static void validateSpanData(List<Span> traceSpanList, String[][] traceTree) {

    }

    private static void validateTraceId(List<Span> traceSpanList) {
        String traceId = null;
        for (Span span : traceSpanList) {

        }
    }
}
